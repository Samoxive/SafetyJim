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
            DiscordUtils.failMessage(bot, message, "Could not find the user to softban!")
            return false
        }
        val softbanUser = mentionedUsers[0]
        val softbanMember = guild.getMember(softbanUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        if (user.id == softbanUser.id) {
            DiscordUtils.failMessage(bot, message, "You can't softban yourself, dummy!")
            return false
        }

        if (!DiscordUtils.isBannable(softbanMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        val arguments = TextUtils.seekScannerToEnd(messageIterator)
        val argumentsSplit = arguments.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var reason = argumentsSplit[0]
        reason = if (reason == "") "No reason specified" else reason.trim { it <= ' ' }
        var timeArgument: String? = null

        if (argumentsSplit.size > 1) {
            timeArgument = argumentsSplit[1]
        }

        val days: Int

        days = if (timeArgument != null) {
            try {
                Integer.parseInt(timeArgument.trim { it <= ' ' })
            } catch (e: NumberFormatException) {
                DiscordUtils.failMessage(bot, message, "Invalid day count, please try again.")
                return false
            }

        } else {
            1
        }

        if (days < 1 || days > 7) {
            DiscordUtils.failMessage(bot, message, "The amount of days must be between 1 and 7.")
            return false
        }

        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Softbanned from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were softbanned from " + guild.name)
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false)
        embed.setFooter("Softbanned by " + DiscordUtils.getUserTagAndId(user), null)
        embed.setTimestamp(now.toInstant())

        DiscordUtils.sendDM(softbanUser, embed.build())

        try {
            val auditLogReason = String.format("Softbanned by %s - %s", DiscordUtils.getUserTagAndId(user), reason)
            controller.ban(softbanMember, days, auditLogReason).complete()
            controller.unban(softbanUser).complete()

            val database = bot.database

            val record = database.insertInto(Tables.SOFTBANLIST,
                    Tables.SOFTBANLIST.USERID,
                    Tables.SOFTBANLIST.MODERATORUSERID,
                    Tables.SOFTBANLIST.GUILDID,
                    Tables.SOFTBANLIST.SOFTBANTIME,
                    Tables.SOFTBANLIST.DELETEDAYS,
                    Tables.SOFTBANLIST.REASON)
                    .values(softbanUser.id,
                            user.id,
                            guild.id,
                            now.time / 1000,
                            days,
                            reason)
                    .returning(Tables.SOFTBANLIST.ID)
                    .fetchOne()

            DiscordUtils.createModLogEntry(bot, shard, message, softbanMember, reason, "softban", record.id!!, null, false)
            DiscordUtils.sendMessage(channel, "Softbanned " + DiscordUtils.getUserTagAndId(softbanUser))
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not softban the specified user. Do I have enough permissions?")
        }

        return false
    }
}
