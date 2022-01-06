package org.samoxive.safetyjim.discord

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.utils.SessionControllerAdapter
import org.samoxive.safetyjim.config.Config
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.database.SettingsTable.getGuildSettings
import org.samoxive.safetyjim.discord.commands.*
import org.samoxive.safetyjim.discord.processors.InviteLink
import org.samoxive.safetyjim.discord.processors.MessageStats
import org.samoxive.safetyjim.discord.processors.WordFilter
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
        "melo" to Melo(),
        "xkcd" to Xkcd(),
        "weather" to Weather(),
        "whois" to Whois(),
        "massban" to MassBan()
    )

    val processors = listOf(InviteLink(), MessageStats(), WordFilter())
    val startTime = Date()

    val guildCount: Long
        get() = shards.asSequence().map { shard -> shard.jda }
            .map { shard -> shard.guildCache.size() }
            .sum()

    val notifiedGuilds = mutableSetOf<Long>()

    init {
        val sessionController = SessionControllerAdapter()
        for (i in 0 until config.jim.shard_count) {
            val shard = DiscordShard(this, i, sessionController)
            shards.add(shard)
        }

        scheduleJob(10, 5, "allowUsers") { allowUsers() }
        scheduleJob(10, 10, "unmuteUsers") { unmuteUsers() }
        scheduleJob(10, 30, "unbanUsers") { unbanUsers() }
        scheduleJob(10, 5, "remindReminders") { remindReminders() }

        val inviteLink = shards[0].jda.getInviteUrl(
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

    fun getGuild(guildId: String): Guild? {
        val guildIdLong = tryhard { guildId.toLong() } ?: return null
        val shardId = getShardIdFromGuildId(guildIdLong, shards.size)
        return shards[shardId].jda.getGuildById(guildId)
    }

    fun getGuildShard(guildId: Long): JDA {
        val shardId = getShardIdFromGuildId(guildId, shards.size)
        return shards[shardId].jda
    }

    private suspend fun allowUsers() {
        val usersToBeAllowed = JoinsTable.fetchExpiredJoins()

        for (user in usersToBeAllowed) {
            val guildId = user.guildId
            val shardId = getShardIdFromGuildId(guildId, config.jim.shard_count)
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                JoinsTable.updateJoin(user.copy(allowed = true))
                continue
            }

            val guildSettings = getGuildSettings(guild, config)
            val enabled = guildSettings.holdingRoom

            if (enabled) {
                val guildUser = shard.jda.retrieveUserById(user.userId).await()
                val member = tryhardAsync { guild.retrieveMember(guildUser, true).await() }
                val roleId = guildSettings.holdingRoomRoleId
                val role = if (roleId != null) guild.getRoleById(roleId) else null

                if (role == null) {
                    SettingsTable.updateSettings(guildSettings.copy(holdingRoom = false))
                    continue
                }

                tryhardAsync { guild.addRoleToMember(member!!, role).await() }
            }

            JoinsTable.updateJoin(user.copy(allowed = true))
        }
    }

    private suspend fun unbanUsers() {
        val usersToBeUnbanned = BansTable.fetchExpiredBans()

        for (user in usersToBeUnbanned) {
            val guildId = user.guildId
            val shardId = getShardIdFromGuildId(guildId, config.jim.shard_count)
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                BansTable.updateBan(user.copy(unbanned = true))
                continue
            }

            val guildUser = shard.jda.retrieveUserById(user.userId).await()

            if (!guild.selfMember.hasPermission(Permission.BAN_MEMBERS)) {
                BansTable.updateBan(user.copy(unbanned = true))
                continue
            }

            val banRecord = tryhardAsync { guild.retrieveBanList().await() }
                ?.firstOrNull { ban -> ban.user.id == guildUser.id }

            if (banRecord == null) {
                BansTable.updateBan(user.copy(unbanned = true))
                continue
            }

            tryhardAsync { guild.unban(guildUser).await() }
            BansTable.updateBan(user.copy(unbanned = true))
        }
    }

    private suspend fun unmuteUsers() {
        val usersToBeUnmuted = MutesTable.fetchExpiredMutes()

        for (user in usersToBeUnmuted) {
            val guildId = user.guildId
            val shardId = getShardIdFromGuildId(guildId, config.jim.shard_count)
            val shard = shards[shardId]
            val shardClient = shard.jda
            val guild = shardClient.getGuildById(guildId)

            if (guild == null) {
                MutesTable.updateMute(user.copy(unmuted = true))
                continue
            }

            val guildUser = shard.jda.retrieveUserById(user.userId).await()
            val member = tryhardAsync { guild.retrieveMember(guildUser, true).await() }
            if (member == null) {
                MutesTable.updateMute(user.copy(unmuted = true))
                continue
            }

            val mutedRoles = guild.getRolesByName("Muted", false)
            val role = if (mutedRoles.isEmpty()) {
                tryhardAsync { setupMutedRole(guild) }
            } else {
                mutedRoles[0]
            }

            if (role == null) {
                MutesTable.updateMute(user.copy(unmuted = true))
                continue
            }

            tryhardAsync { guild.removeRoleFromMember(member, role).await() }
            MutesTable.updateMute(user.copy(unmuted = true))
        }
    }

    private suspend fun remindReminders() {
        val reminders = RemindersTable.fetchExpiredReminders()

        for (reminder in reminders) {
            val guildId = reminder.guildId
            val channelId = reminder.channelId
            val userId = reminder.userId
            val shardId = getShardIdFromGuildId(guildId, config.jim.shard_count)
            val shard = shards[shardId].jda
            val guild = shard.getGuildById(guildId)
            val user = shard.retrieveUserById(userId).await()

            if (guild == null) {
                RemindersTable.updateReminder(reminder.copy(reminded = true))
                continue
            }

            val channel = guild.getTextChannelById(channelId)
            val member = tryhardAsync { guild.retrieveMember(user, true).await() }

            val embed = EmbedBuilder()
            embed.setTitle("Reminder - #${reminder.id}")
            embed.setDescription(reminder.message)
            embed.setAuthor("Safety Jim", null, shard.selfUser.avatarUrl)
            embed.setFooter("Reminder set on", null)
            embed.setTimestamp(Date(reminder.createTime * 1000).toInstant())
            embed.setColor(Color(0x4286F4))

            if (channel == null || member == null) {
                user.trySendMessage(embed.build())
            } else {
                try {
                    val builder = MessageBuilder()
                    builder.append(user.asMention)
                    builder.setEmbeds(embed.build())

                    channel.sendMessage(builder.build()).await()
                } catch (e: Exception) {
                    user.trySendMessage(embed.build())
                }
            }

            RemindersTable.updateReminder(reminder.copy(reminded = true))
        }
    }
}
