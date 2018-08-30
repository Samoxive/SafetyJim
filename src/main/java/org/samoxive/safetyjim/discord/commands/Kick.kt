package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimKick
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils
import java.awt.Color
import java.util.*

class Kick : Command() {
    override val usages = arrayOf("kick @user [reason] - kicks the user with the specified reason")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val shard = event.jda

        val member = event.member
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Kick Members")
            return false
        }

        if (!messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            return true
        } else {
            // advance the scanner one step to get rid of user mention
            messageIterator.next()
        }

        val mentionedUsers = message.mentionedUsers
        if (mentionedUsers.isEmpty()) {
            DiscordUtils.failMessage(bot, message, "Could not find the user to kick!")
            return false
        }
        val kickUser = mentionedUsers[0]
        val kickMember = guild.getMember(kickUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        if (user.id == kickUser.id) {
            DiscordUtils.failMessage(bot, message, "You can't kick yourself, dummy!")
            return false
        }

        if (!DiscordUtils.isKickable(kickMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        var reason = TextUtils.seekScannerToEnd(messageIterator)
        reason = if (reason == "") "No reason specified" else reason

        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Kicked from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were kicked from " + guild.name)
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false)
        embed.setFooter("Kicked by " + DiscordUtils.getUserTagAndId(user), null)
        embed.setTimestamp(now.toInstant())

        DiscordUtils.sendDM(kickUser, embed.build())

        try {
            val auditLogReason = String.format("Kicked by %s - %s", DiscordUtils.getUserTagAndId(user), reason)
            controller.kick(kickMember, auditLogReason).complete()
            DiscordUtils.successReact(bot, message)

            val record = transaction {
                JimKick.new {
                    userid = kickUser.id
                    moderatoruserid = user.id
                    guildid = guild.id
                    kicktime = now.time / 1000
                    this.reason = reason
                }
            }

            DiscordUtils.createModLogEntry(bot, shard, message, kickMember, reason, "kick", record.id.value, null, false)
            DiscordUtils.sendMessage(channel, "Kicked " + DiscordUtils.getUserTagAndId(kickUser))
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not kick the specified user. Do I have enough permissions?")
        }

        return false
    }
}
