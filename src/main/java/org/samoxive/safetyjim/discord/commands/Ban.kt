package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimBan
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

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
            message.failMessage(bot, "You don't have enough permissions to execute this command! Required permission: Ban Members")
            return false
        }

        val (searchResult, banUser) = messageIterator.findUser(message, true)
        if (searchResult == SearchUserResult.NOT_FOUND || (banUser == null)) {
            message.failMessage(bot, "Could not find the user to ban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, banUser) ?: return false
        }

        val banMember = guild.getMember(banUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage(bot, "I don't have enough permissions to do that!")
            return false
        }

        if (user == banUser) {
            message.failMessage(bot, "You can't ban yourself, dummy!")
            return false
        }

        if (banMember != null && !banMember.isBannableBy(selfMember)) {
            message.failMessage(bot, "I don't have enough permissions to do that!")
            return false
        }

        val parsedReasonAndTime = try {
            messageIterator.getTextAndTime()
        } catch (e: InvalidTimeInputException) {
            message.failMessage(bot, "Invalid time argument. Please try again.")
            return false
        } catch (e: TimeInputInPastException) {
            message.failMessage(bot, "Your time argument was set for the past. Try again.\n" + "If you're specifying a date, e.g. `30 December`, make sure you also write the year.")
            return false
        }

        val (text, expirationDate) = parsedReasonAndTime
        val reason = if (text == "") "No reason specified" else text
        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Banned from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were banned from " + guild.name)
        embed.addField("Reason:", truncateForEmbed(reason), false)
        embed.addField("Banned until", expirationDate?.toString() ?: "Indefinitely", false)
        embed.setFooter("Banned by " + user.getUserTagAndId(), null)
        embed.setTimestamp(now.toInstant())

        banUser.sendDM(embed.build())

        try {
            val auditLogReason = String.format("Banned by %s - %s", user.getUserTagAndId(), reason)
            controller.ban(banUser, 0, auditLogReason).complete()
            message.successReact(bot)

            val expires = expirationDate != null

            val record = transaction {
                JimBan.new {
                    userid = banUser.id
                    moderatoruserid = user.id
                    guildid = guild.id
                    bantime = now.time / 1000
                    expiretime = if (expirationDate != null) expirationDate.time / 1000 else 0
                    this.reason = reason
                    this.expires = expires
                    unbanned = false
                }
            }

            val banId = record.id.value
            message.createModLogEntry(bot, shard, banUser, reason, "ban", banId, expirationDate, true)
            channel.trySendMessage("Banned ${banUser.getUserTagAndId()} ${getExpirationTextInChannel(expirationDate)}")
        } catch (e: Exception) {
            message.failMessage(bot, "Could not ban the specified user. Do I have enough permissions?")
        }

        return false
    }
}
