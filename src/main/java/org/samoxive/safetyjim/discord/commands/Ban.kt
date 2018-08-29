package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.jooq.generated.Tables
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils

import java.awt.*
import java.util.Date
import java.util.Scanner

class Ban : Command() {
    override val usages = arrayOf("ban @user [reason] | [time] - bans the user with specific arguments. Both arguments can be omitted")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val shard = event.jda

        val member = event.member
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Ban Members")
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
            DiscordUtils.failMessage(bot, message, "Could not find the user to ban!")
            return false
        }
        val banUser = mentionedUsers[0]
        val banMember = guild.getMember(banUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        if (user.id == banUser.id) {
            DiscordUtils.failMessage(bot, message, "You can't ban yourself, dummy!")
            return false
        }

        if (!DiscordUtils.isBannable(banMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        val parsedReasonAndTime = try {
            TextUtils.getTextAndTime(messageIterator)
        } catch (e: TextUtils.InvalidTimeInputException) {
            DiscordUtils.failMessage(bot, message, "Invalid time argument. Please try again.")
            return false
        } catch (e: TextUtils.TimeInputInPastException) {
            DiscordUtils.failMessage(bot, message, "Your time argument was set for the past. Try again.\n" + "If you're specifying a date, e.g. `30 December`, make sure you also write the year.")
            return false
        }

        val (text, expirationDate) = parsedReasonAndTime
        val reason = if (text == "") "No reason specified" else text
        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Banned from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were banned from " + guild.name)
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false)
        embed.addField("Banned until", expirationDate?.toString() ?: "Indefinitely", false)
        embed.setFooter("Banned by " + DiscordUtils.getUserTagAndId(user), null)
        embed.setTimestamp(now.toInstant())

        DiscordUtils.sendDM(banUser, embed.build())

        try {
            val auditLogReason = String.format("Banned by %s - %s", DiscordUtils.getUserTagAndId(user), reason)
            controller.ban(banMember, 0, auditLogReason).complete()
            DiscordUtils.successReact(bot, message)

            val expires = expirationDate != null
            val database = bot.database

            val record = database.insertInto(Tables.BANLIST,
                    Tables.BANLIST.USERID,
                    Tables.BANLIST.MODERATORUSERID,
                    Tables.BANLIST.GUILDID,
                    Tables.BANLIST.BANTIME,
                    Tables.BANLIST.EXPIRETIME,
                    Tables.BANLIST.REASON,
                    Tables.BANLIST.EXPIRES,
                    Tables.BANLIST.UNBANNED)
                    .values(banUser.id,
                            user.id,
                            guild.id,
                            now.time / 1000,
                            if (expirationDate != null) expirationDate.time / 1000 else 0,
                            reason,
                            expires,
                            false)
                    .returning(Tables.BANLIST.ID)
                    .fetchOne()

            val banId = record.id
            DiscordUtils.createModLogEntry(bot, shard, message, banMember, reason, "ban", banId, expirationDate, true)
            DiscordUtils.sendMessage(channel, "Banned " + DiscordUtils.getUserTagAndId(banUser))
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not ban the specified user. Do I have enough permissions?")
        }

        return false
    }
}
