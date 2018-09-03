package org.samoxive.safetyjim.discord

import com.mashape.unirest.http.Unirest
import com.uchuhimo.konf.Config
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
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
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class DiscordBot(val config: Config) {
    private val log = LoggerFactory.getLogger(DiscordBot::class.java)
    val shards = ArrayList<DiscordShard>()
    val commands = HashMap<String, Command>()
    val processors = ArrayList<MessageProcessor>()
    private val scheduler = Executors.newScheduledThreadPool(8)
    val startTime = Date()

    val guildCount: Long
        get() = shards.map { shard -> shard.shard }
                .map { shard -> shard.guildCache.size() }
                .sum()

    init {
        loadCommands()
        loadProcessors()

        val sessionController = SessionControllerAdapter()
        for (i in 0 until config[JimConfig.shard_count]) {
            val shard = DiscordShard(this, i, sessionController)
            shards.add(shard)

            // Discord API rate limits login requests to once per 5 seconds
            try {
                Thread.sleep(5000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        scheduler.scheduleAtFixedRate({
            try {
                allowUsers()
            } catch (e: Exception) {
                log.error("Exception occured in allowUsers", e)
            }
        }, 10, 5, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({
            try {
                unmuteUsers()
            } catch (e: Exception) {
                log.error("Exception occured in unmuteUsers", e)
            }
        }, 10, 10, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({
            try {
                unbanUsers()
            } catch (e: Exception) {
                log.error("Exception occured in unbanUsers", e)
            }
        }, 10, 30, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({
            try {
                remindReminders()
            } catch (e: Exception) {
                log.error("Exception occured in remindReminders", e)
            }
        }, 10, 5, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({
            try {
                saveMemberCounts()
            } catch (e: Exception) {
                log.error("Exception occured in saveMemberCounts", e)
            }
        }, 1, 10, TimeUnit.MINUTES)
        scheduler.scheduleAtFixedRate({
            try {
                updateBotLists()
            } catch (e: Exception) {
                log.error("Exception occured in updateBotLists", e)
            }
        }, 10, 1, TimeUnit.MINUTES)

        val inviteLink = shards[0].shard.asBot().getInviteUrl(
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

    private fun loadCommands() {
        commands["ping"] = Ping()
        commands["unmute"] = Unmute()
        commands["invite"] = Invite()
        commands["ban"] = Ban()
        commands["kick"] = Kick()
        commands["mute"] = Mute()
        commands["warn"] = Warn()
        commands["help"] = Help()
        commands["clean"] = Clean()
        commands["tag"] = Tag()
        commands["remind"] = Remind()
        commands["info"] = Info()
        commands["settings"] = Settings()
        commands["softban"] = Softban()
        commands["unban"] = Unban()
        commands["server"] = Server()
        commands["iam"] = Iam()
        commands["role"] = RoleCommand()
        commands["hardban"] = Hardban()
    }

    private fun loadProcessors() {
        processors.add(InviteLink())
        processors.add(MessageStats())
    }

    private fun saveMemberCounts() = transaction {
        val settings = getAllGuildSettings()
        shards.map { shard -> shard.shard.guilds }
                .flatMap { it }
                .filter { guild -> settings[guild.id]!!.statistics }
                .forEach { guild ->
                    val onlineCount = guild.members.filter { member -> DiscordUtils.isOnline(member) }.count()
                    JimMemberCount.new {
                        guildid = guild.id
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
            val shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.shard
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                user.allowed = true
                continue
            }

            val guildSettings = getGuildSettings(guild, config)
            val enabled = guildSettings.holdingroom

            if (enabled) {
                val guildUser = DiscordUtils.getUserById(shard.shard, user.userid)
                val member = guild.getMember(guildUser)
                val roleId = guildSettings.holdingroomroleid
                val role = guild.getRoleById(roleId)
                val controller = guild.controller

                if (role == null) {
                    guildSettings.holdingroom = false
                    continue
                }

                try {
                    controller.addSingleRoleToMember(member, role).complete()
                } catch (e: Exception) {
                    //
                } finally {
                    user.allowed = true
                }
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
            val shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.shard
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                user.unbanned = true
                continue
            }

            val guildUser = DiscordUtils.getUserById(shard.shard, user.userid)
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

            try {
                controller.unban(guildUser).complete()
            } catch (e: Exception) {
                //
            } finally {
                user.unbanned = true
            }
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
            val shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.shard
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                transaction { user.unmuted = true }
                continue
            }

            val guildUser = DiscordUtils.getUserById(shard.shard, user.userid)
            val member = guild.getMember(guildUser)
            if (member == null) {
                user.unmuted = true
                continue
            }

            val mutedRoles = guild.getRolesByName("Muted", false)
            val role = if (mutedRoles.isEmpty()) {
                try {
                    Mute.setupMutedRole(guild)
                } catch (e: Exception) {
                    null
                }
            } else {
                mutedRoles[0]
            }

            if (role == null) {
                user.unmuted = true
                continue
            }

            val controller = guild.controller

            try {
                controller.removeSingleRoleFromMember(member, role).complete()
            } finally {
                user.unmuted = true
            }
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
            val shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config[JimConfig.shard_count])
            val shard = shards[shardId].shard
            val guild = shard.getGuildById(guildId)
            val user = DiscordUtils.getUserById(shard, userId)

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
                DiscordUtils.sendDM(user, embed.build())
            } else {
                try {
                    val builder = MessageBuilder()
                    builder.append(user.asMention)
                    builder.setEmbed(embed.build())

                    channel.sendMessage(builder.build()).complete()
                } catch (e: Exception) {
                    DiscordUtils.sendDM(user, embed.build())
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
        val clientId = shards[0].shard.selfUser.id
        for (list in config[BotListConfig.list]) {
            val body = JSONObject().put("server_count", guildCount)
            Unirest.post(list.url.replace("\$id", clientId))
                    .header("Authorization", list.token)
                    .body(body)
                    .asBinaryAsync()
        }
    }
}
