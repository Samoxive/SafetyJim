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

class Warn : Command() {
    override val usages = arrayOf("warn @user [reason] - warn the user with the specified reason")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val shard = event.jda

        val member = event.member
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command!")
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
            DiscordUtils.failMessage(bot, message, "Could not find the user to warn!")
            return false
        }
        val warnUser = mentionedUsers[0]
        val warnMember = guild.getMember(warnUser)

        if (user.id == warnUser.id) {
            DiscordUtils.failMessage(bot, message, "You can't warn yourself, dummy!")
            return false
        }

        var reason = TextUtils.seekScannerToEnd(messageIterator)
        reason = if (reason == "") "No reason specified" else reason

        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Warned in " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were warned in " + guild.name)
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false)
        embed.setFooter("Warned by " + DiscordUtils.getUserTagAndId(user), null)
        embed.setTimestamp(now.toInstant())

        try {
            DiscordUtils.sendDM(warnUser, embed.build())
        } catch (e: Exception) {
            DiscordUtils.sendMessage(channel, "Could not send a warning to the specified user via private message!")
        }

        DiscordUtils.successReact(bot, message)

        val database = bot.database

        val record = database.insertInto(Tables.WARNLIST,
                Tables.WARNLIST.USERID,
                Tables.WARNLIST.MODERATORUSERID,
                Tables.WARNLIST.GUILDID,
                Tables.WARNLIST.WARNTIME,
                Tables.WARNLIST.REASON)
                .values(warnUser.id,
                        user.id,
                        guild.id,
                        now.time / 1000,
                        reason)
                .returning(Tables.WARNLIST.ID)
                .fetchOne()

        DiscordUtils.createModLogEntry(bot, shard, message, warnMember, reason, "warn", record.id!!, null, false)
        DiscordUtils.sendMessage(channel, "Warned " + DiscordUtils.getUserTagAndId(warnUser))

        return false
    }
}
