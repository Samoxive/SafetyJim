package org.samoxive.safetyjim.discord

import com.mashape.unirest.http.Unirest
import com.uchuhimo.konf.Config
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.utils.SessionControllerAdapter
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.json.JSONObject
import org.samoxive.safetyjim.config.BotListConfig
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.discord.commands.*
import org.samoxive.safetyjim.discord.processors.InviteLink
import org.samoxive.safetyjim.discord.processors.MessageStats
import org.samoxive.safetyjim.tryAndLog
import org.samoxive.safetyjim.tryhard
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class DiscordBot(val config: Config) {
    private val log = LoggerFactory.getLogger(DiscordBot::class.java)
    val shards = ArrayList<DiscordShard>()
    val commands = mapOf(
            "ping" to Ping(),
            "unmute" to Unmute(),
            "invite" to Invite(),
            "ban" to Ban(),
            "kick" to Kick(),
            "mute" to Mute(),
            "warn" to Warn(),
            "help" to Help(),
            "clean" to Clean(),
            "tag" to Tag(),
            "remind" to Remind(),
            "info" to Info(),
            "settings" to Settings(),
            "softban" to Softban(),
            "unban" to Unban(),
            "server" to Server(),
            "iam" to Iam(),
            "role" to RoleCommand(),
            "hardban" to Hardban(),
            "melo" to Melo()
    )
    val deleteCommands = arrayOf("ban", "kick", "mute", "softban", "warn", "hardban")
    val processors = listOf(InviteLink(), MessageStats())
    private val scheduler = Executors.newScheduledThreadPool(8)
    val startTime = Date()

    val guildCount: Long
        get() = shards.asSequence().map { shard -> shard.jda }
                .map { shard -> shard.guildCache.size() }
                .sum()

    init {
        val sessionController = SessionControllerAdapter()
        for (i in 0 until config[JimConfig.shard_count]) {
            val shard = DiscordShard(this, i, sessionController)
            shards.add(shard)

            // Discord API rate limits login requests to once per 5 seconds
            Thread.sleep(5000)
        }

        scheduler.scheduleAtFixedRate({ tryAndLog(log, "allowUsers") { allowUsers() } }, 10, 5, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({ tryAndLog(log, "unmuteUsers") { unmuteUsers() } }, 10, 10, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({ tryAndLog(log, "unbanUsers") { unbanUsers() } }, 10, 30, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({ tryAndLog(log, "remindReminders") { remindReminders() } }, 10, 5, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({ tryAndLog(log, "saveMemberCounts") { saveMemberCounts() } }, 1, 10, TimeUnit.MINUTES)
        scheduler.scheduleAtFixedRate({ tryAndLog(log, "updateBotLists") { updateBotLists() } }, 10, 1, TimeUnit.MINUTES)

        val inviteLink = shards[0].jda.asBot().getInviteUrl(
                Permission.KICK_MEMBERS,
                Permission.BAN_MEMBERS,
                Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_READ,
                Permission.MESSAGE_WRITE,
                Permission.MESSAGE_MANAGE,
                Permission.MANAGE_ROLES
        )
        log.info("All shards ready.")
        log.info("Bot invite link: $inviteLink")
    }

    fun getGuildFromBot(guildId: String): Guild? {
        val guildIdLong = tryhard { guildId.toLong() } ?: return null
        val shardId = getShardIdFromGuildId(guildIdLong, shards.size)
        return shards[shardId].jda.getGuildById(guildId)
    }

    private fun saveMemberCounts() = transaction {
        val settings = getAllGuildSettings()
        shards.map { shard -> shard.jda.guilds }
                .flatMap { it }
                .filter { guild -> settings[guild.idLong]?.statistics ?: false }
                .forEach { guild ->
                    val onlineCount = guild.members.asSequence().filter { member -> member.isOnline() }.count()
                    JimMemberCount.new {
                        guildid = guild.idLong
                        date = Date().time
                        onlinecount = onlineCount
                        count = guild.members.size
                    }
                }
    }

    private fun allowUsers() = transaction {
        val currentTime = System.currentTimeMillis() / 1000

        val usersToBeAllowed = JimJoin.find {
            (JimJoinTable.allowed eq false) and (JimJoinTable.allowtime less currentTime)
        }

        for (user in usersToBeAllowed) {
            val guildId = user.guildid
            val guildIdLong = guildId.toLong()
            val shardId = getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                user.allowed = true
                continue
            }

            val guildSettings = getGuildSettings(guild, config)
            val enabled = guildSettings.holdingroom

            if (enabled) {
                val guildUser = shard.jda.retrieveUserById(user.userid).complete()
                val member = guild.getMember(guildUser)
                val roleId = guildSettings.holdingroomroleid
                val role = if (roleId != null) guild.getRoleById(roleId) else null
                val controller = guild.controller

                if (role == null) {
                    guildSettings.holdingroom = false
                    continue
                }

                tryhard {
                    controller.addSingleRoleToMember(member, role).complete()
                }
                user.allowed = true
            } else {
                user.allowed = true
            }
        }
    }

    private fun unbanUsers() = transaction {
        val currentTime = System.currentTimeMillis() / 1000

        val usersToBeUnbanned = JimBan.find {
            (JimBanTable.unbanned eq false) and (JimBanTable.expires eq true) and (JimBanTable.expiretime less currentTime)
        }

        for (user in usersToBeUnbanned) {
            val guildId = user.guildid
            val guildIdLong = guildId.toLong()
            val shardId = getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                user.unbanned = true
                continue
            }

            val guildUser = shard.jda.retrieveUserById(user.userid).complete()
            val controller = guild.controller

            if (!guild.selfMember.hasPermission(Permission.BAN_MEMBERS)) {
                user.unbanned = true
                continue
            }

            val banRecord = guild.banList
                    .complete()
                    .firstOrNull { ban -> ban.user.id == guildUser.id }

            if (banRecord == null) {
                user.unbanned = true
            }

            tryhard {
                controller.unban(guildUser).complete()
            }
            user.unbanned = true
        }
    }

    private fun unmuteUsers() = transaction {
        val currentTime = System.currentTimeMillis() / 1000

        val usersToBeUnmuted = JimMute.find {
            (JimMuteTable.unmuted eq false) and (JimMuteTable.expires eq true) and (JimMuteTable.expiretime less currentTime)
        }

        for (user in usersToBeUnmuted) {
            val guildId = user.guildid
            val guildIdLong = guildId.toLong()
            val shardId = getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                user.unmuted = true
                continue
            }

            val guildUser = shard.jda.retrieveUserById(user.userid).complete()
            val member = guild.getMember(guildUser)
            if (member == null) {
                user.unmuted = true
                continue
            }

            val mutedRoles = guild.getRolesByName("Muted", false)
            val role = if (mutedRoles.isEmpty()) {
                tryhard { Mute.setupMutedRole(guild) }
            } else {
                mutedRoles[0]
            }

            if (role == null) {
                user.unmuted = true
                continue
            }

            val controller = guild.controller

            tryhard {
                controller.removeSingleRoleFromMember(member, role).complete()
            }
            user.unmuted = true
        }
    }

    private fun remindReminders() = transaction {
        val now = Date().time / 1000

        val reminders = JimReminder.find {
            (JimReminderTable.reminded eq false) and (JimReminderTable.remindtime less now)
        }

        for (reminder in reminders) {
            val guildId = reminder.guildid
            val guildIdLong = guildId.toLong()
            val channelId = reminder.channelid
            val userId = reminder.userid
            val shardId = getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId].jda
            val guild = shard.getGuildById(guildId)
            val user = shard.retrieveUserById(userId).complete()

            if (guild == null) {
                reminder.reminded = true
                continue
            }

            val channel = guild.getTextChannelById(channelId)
            val member = guild.getMember(user)

            val embed = EmbedBuilder()
            embed.setTitle("Reminder - #${reminder.id}")
            embed.setDescription(reminder.message)
            embed.setAuthor("Safety Jim", null, shard.selfUser.avatarUrl)
            embed.setFooter("Reminder set on", null)
            embed.setTimestamp(Date(reminder.remindtime * 1000).toInstant())
            embed.setColor(Color(0x4286F4))

            if (channel == null || member == null) {
                user.sendDM(embed.build())
            } else {
                try {
                    val builder = MessageBuilder()
                    builder.append(user.asMention)
                    builder.setEmbed(embed.build())

                    channel.sendMessage(builder.build()).complete()
                } catch (e: Exception) {
                    user.sendDM(embed.build())
                }
            }

            reminder.reminded = true
        }
    }

    private fun updateBotLists() {
        if (!config[BotListConfig.enabled]) {
            return
        }

        val guildCount = guildCount
        val clientId = shards[0].jda.selfUser.id
        for (list in config[BotListConfig.list]) {
            val body = JSONObject().put("server_count", guildCount)
            Unirest.post(list.url.replace("\$id", clientId))
                    .header("Authorization", list.token)
                    .body(body)
                    .asBinaryAsync()
        }
    }
}
