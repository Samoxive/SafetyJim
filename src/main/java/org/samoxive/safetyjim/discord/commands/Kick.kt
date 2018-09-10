package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimKick
import org.samoxive.safetyjim.discord.*
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
            message.failMessage(bot, "You don't have enough permissions to execute this command! Required permission: Kick Members")
            return false
        }

        val (searchResult, kickUser) = messageIterator.findUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (kickUser == null)) {
            message.failMessage(bot, "Could not find the user to kick!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, kickUser) ?: return false
        }

        val kickMember = guild.getMember(kickUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
            message.failMessage(bot, "I don't have enough permissions to do that!")
            return false
        }

        if (user == kickUser) {
            message.failMessage(bot, "You can't kick yourself, dummy!")
            return false
        }

        if (kickMember != null && !kickMember.isKickableBy(selfMember)) {
            message.failMessage(bot, "I don't have enough permissions to do that!")
            return false
        }

        var reason = messageIterator.seekToEnd()
        reason = if (reason == "") "No reason specified" else reason

        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Kicked from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were kicked from " + guild.name)
        embed.addField("Reason:", truncateForEmbed(reason), false)
        embed.setFooter("Kicked by " + user.getUserTagAndId(), null)
        embed.setTimestamp(now.toInstant())

        kickUser.sendDM(embed.build())

        try {
            val auditLogReason = String.format("Kicked by %s - %s", user.getUserTagAndId(), reason)
            controller.kick(kickUser.id, auditLogReason).complete()
            message.successReact(bot)

            val record = transaction {
                JimKick.new {
                    userid = kickUser.id
                    moderatoruserid = user.id
                    guildid = guild.id
                    kicktime = now.time / 1000
                    this.reason = reason
                }
            }

            message.createModLogEntry(bot, shard, kickUser, reason, "kick", record.id.value, null, false)
            channel.sendMessage("Kicked " + kickUser.getUserTagAndId())
        } catch (e: Exception) {
            message.failMessage(bot, "Could not kick the specified user. Do I have enough permissions?")
        }

        return false
    }
}
