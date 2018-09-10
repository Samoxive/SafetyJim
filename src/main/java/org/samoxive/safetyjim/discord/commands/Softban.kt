package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimSoftban
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

class Softban : Command() {
    override val usages = arrayOf("softban @user [reason] | [messages to delete (days)] - softbans the user with the specified args.")

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

        val (searchResult, softbanUser) = messageIterator.findUser(message, isForBan = true)
        if (searchResult == SearchUserResult.NOT_FOUND || (softbanUser == null)) {
            message.failMessage(bot, "Could not find the user to softban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, softbanUser) ?: return false
        }

        val softbanMember = guild.getMember(softbanUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage(bot, "I don't have enough permissions to do that!")
            return false
        }

        if (user == softbanUser) {
            message.failMessage(bot, "You can't softban yourself, dummy!")
            return false
        }

        if (softbanMember != null && !softbanMember.isBannableBy(selfMember)) {
            message.failMessage(bot, "I don't have enough permissions to do that!")
            return false
        }

        val arguments = messageIterator.seekToEnd()
        val argumentsSplit = arguments.split("\\|").toTypedArray()
        var reason = argumentsSplit[0]
        reason = if (reason == "") "No reason specified" else reason.trim()
        var timeArgument: String? = null

        if (argumentsSplit.size > 1) {
            timeArgument = argumentsSplit[1]
        }

        val days: Int

        days = if (timeArgument != null) {
            try {
                Integer.parseInt(timeArgument.trim())
            } catch (e: NumberFormatException) {
                message.failMessage(bot, "Invalid day count, please try again.")
                return false
            }
        } else {
            1
        }

        if (days < 1 || days > 7) {
            message.failMessage(bot, "The amount of days must be between 1 and 7.")
            return false
        }

        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Softbanned from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were softbanned from " + guild.name)
        embed.addField("Reason:", truncateForEmbed(reason), false)
        embed.setFooter("Softbanned by " + user.getUserTagAndId(), null)
        embed.setTimestamp(now.toInstant())

        softbanUser.sendDM(embed.build())

        try {
            val auditLogReason = String.format("Softbanned by %s - %s", user.getUserTagAndId(), reason)
            controller.ban(softbanUser, days, auditLogReason).complete()
            controller.unban(softbanUser).complete()

            val record = transaction {
                JimSoftban.new {
                    userid = softbanUser.id
                    moderatoruserid = user.id
                    guildid = guild.id
                    softbantime = now.time / 1000
                    deletedays = days
                    this.reason = reason
                }
            }

            message.createModLogEntry(bot, shard, softbanUser, reason, "softban", record.id.value, null, false)
            channel.sendMessage("Softbanned " + softbanUser.getUserTagAndId())
        } catch (e: Exception) {
            message.failMessage(bot, "Could not softban the specified user. Do I have enough permissions?")
        }

        return false
    }
}
