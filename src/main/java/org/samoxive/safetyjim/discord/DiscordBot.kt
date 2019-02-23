package org.samoxive.safetyjim.discord

import com.uchuhimo.konf.Config
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.utils.SessionControllerAdapter
import org.jetbrains.exposed.sql.and
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.discord.commands.*
import org.samoxive.safetyjim.discord.processors.InviteLink
import org.samoxive.safetyjim.discord.processors.MessageStats
import org.samoxive.safetyjim.tryhard
import org.samoxive.safetyjim.tryhardAsync
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
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

        scheduleJob(10, 5, "allowUsers") { allowUsers() }
        scheduleJob(10, 5, "allowUsers") { allowUsers() }
        scheduleJob(10, 10, "unmuteUsers") { unmuteUsers() }
        scheduleJob(10, 30, "unbanUsers") { unbanUsers() }
        scheduleJob(10, 5, "remindReminders") { remindReminders() }
        scheduleJob(10, 10 * 60, "saveMemberCounts") { saveMemberCounts() }

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

    private fun scheduleJob(delaySeconds: Int, intervalSeconds: Int, jobName: String, job: suspend () -> Unit) {
        GlobalScope.launch {
            delay(delaySeconds * 1000L)
            while (true) {
                try {
                    job()
                } catch (e: Throwable) {
                    log.error("Exception occured in $jobName", e)
                }
                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun getGuildFromBot(guildId: String): Guild? {
        val guildIdLong = tryhard { guildId.toLong() } ?: return null
        val shardId = getShardIdFromGuildId(guildIdLong, shards.size)
        return shards[shardId].jda.getGuildById(guildId)
    }

    private suspend fun saveMemberCounts() {
        val settings = getAllGuildSettings()
        shards.map { shard -> shard.jda.guilds }
                .flatten()
                .filter { guild -> settings[guild.idLong]?.statistics ?: false }
                .forEach { guild ->
                    val onlineCount = guild.members.asSequence().filter { member -> member.isOnline() }.count()
                    awaitTransaction {
                        JimMemberCount.new {
                            guildid = guild.idLong
                            date = Date().time
                            onlinecount = onlineCount
                            count = guild.members.size
                        }
                    }
                }
    }

    private suspend fun allowUsers() {
        val currentTime = System.currentTimeMillis() / 1000

        val usersToBeAllowed = awaitTransaction {
            JimJoin.find {
                (JimJoinTable.allowed eq false) and (JimJoinTable.allowtime less currentTime)
            }.toList()
        }

        for (user in usersToBeAllowed) {
            val guildId = user.guildid
            val shardId = getShardIdFromGuildId(guildId, config[JimConfig.shard_count])
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
                val guildUser = shard.jda.retrieveUserById(user.userid).await()
                val member = guild.getMember(guildUser)
                val roleId = guildSettings.holdingroomroleid
                val role = if (roleId != null) guild.getRoleById(roleId) else null
                val controller = guild.controller

                if (role == null) {
                    awaitTransaction { guildSettings.holdingroom = false }
                    continue
                }

                tryhardAsync { controller.addSingleRoleToMember(member, role).await() }
            }

            awaitTransaction { user.allowed = true }
        }
    }

    private suspend fun unbanUsers() {
        val currentTime = System.currentTimeMillis() / 1000

        val usersToBeUnbanned = awaitTransaction {
            JimBan.find {
                (JimBanTable.unbanned eq false) and (JimBanTable.expires eq true) and (JimBanTable.expiretime less currentTime)
            }.toList()
        }

        for (user in usersToBeUnbanned) {
            val guildId = user.guildid
            val shardId = getShardIdFromGuildId(guildId, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                awaitTransaction { user.unbanned = true }
                continue
            }

            val guildUser = shard.jda.retrieveUserById(user.userid).await()
            val controller = guild.controller

            if (!guild.selfMember.hasPermission(Permission.BAN_MEMBERS)) {
                awaitTransaction { user.unbanned = true }
                continue
            }

            val banRecord = tryhardAsync { guild.banList.await() }
                    ?.firstOrNull { ban -> ban.user.id == guildUser.id }

            if (banRecord == null) {
                awaitTransaction { user.unbanned = true }
                continue
            }

            tryhardAsync { controller.unban(guildUser).await() }
            awaitTransaction { user.unbanned = true }
        }
    }

    private suspend fun unmuteUsers() {
        val currentTime = System.currentTimeMillis() / 1000

        val usersToBeUnmuted = awaitTransaction {
            JimMute.find {
                (JimMuteTable.unmuted eq false) and (JimMuteTable.expires eq true) and (JimMuteTable.expiretime less currentTime)
            }.toList()
        }

        for (user in usersToBeUnmuted) {
            val guildId = user.guildid
            val shardId = getShardIdFromGuildId(guildId, config[JimConfig.shard_count])
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                awaitTransaction { user.unmuted = true }
                continue
            }

            val guildUser = shard.jda.retrieveUserById(user.userid).await()
            val member = guild.getMember(guildUser)
            if (member == null) {
                awaitTransaction { user.unmuted = true }
                continue
            }

            val mutedRoles = guild.getRolesByName("Muted", false)
            val role = if (mutedRoles.isEmpty()) {
                tryhardAsync { Mute.setupMutedRole(guild) }
            } else {
                mutedRoles[0]
            }

            if (role == null) {
                awaitTransaction { user.unmuted = true }
                continue
            }

            val controller = guild.controller

            tryhardAsync { controller.removeSingleRoleFromMember(member, role).await() }
            awaitTransaction { user.unmuted = true }
        }
    }

    private suspend fun remindReminders() {
        val now = Date().time / 1000

        val reminders = awaitTransaction {
            JimReminder.find {
                (JimReminderTable.reminded eq false) and (JimReminderTable.remindtime less now)
            }.toList()
        }

        for (reminder in reminders) {
            val guildId = reminder.guildid
            val channelId = reminder.channelid
            val userId = reminder.userid
            val shardId = getShardIdFromGuildId(guildId, config[JimConfig.shard_count])
            val shard = shards[shardId].jda
            val guild = shard.getGuildById(guildId)
            val user = shard.retrieveUserById(userId).await()

            if (guild == null) {
                awaitTransaction { reminder.reminded = true }
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
                user.trySendMessage(embed.build())
            } else {
                try {
                    val builder = MessageBuilder()
                    builder.append(user.asMention)
                    builder.setEmbed(embed.build())

                    channel.sendMessage(builder.build()).await()
                } catch (e: Exception) {
                    user.trySendMessage(embed.build())
                }
            }

            awaitTransaction { reminder.reminded = true }
        }
    }
}
