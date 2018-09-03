package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimHardban
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

class Hardban : Command() {
    override val usages = arrayOf("hardban @user [reason] - hard bans the user with specific arguments. Both arguments can be omitted.")

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

        val (searchResult, hardbanUser) = messageIterator.findUser(message, true)
        if (searchResult == SearchUserResult.NOT_FOUND || (hardbanUser == null)) {
            DiscordUtils.failMessage(bot, message, "Could not find the user to hardban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            askConfirmation(bot, message, hardbanUser) ?: return false
        }

        val hardbanMember = guild.getMember(hardbanUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        if (user == hardbanUser) {
            DiscordUtils.failMessage(bot, message, "You can't hardban yourself, dummy!")
            return false
        }

        if (hardbanMember != null && !DiscordUtils.isBannable(hardbanMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!")
            return false
        }

        var reason = messageIterator.seekToEnd()
        reason = if (reason == "") "No reason specified" else reason

        val now = Date()

        val embed = EmbedBuilder()
        embed.setTitle("Hardbanned from " + guild.name)
        embed.setColor(Color(0x4286F4))
        embed.setDescription("You were hardbanned from " + guild.name)
        embed.addField("Reason:", truncateForEmbed(reason), false)
        embed.setFooter("Hardbanned by " + DiscordUtils.getUserTagAndId(user), null)
        embed.setTimestamp(now.toInstant())

        DiscordUtils.sendDM(hardbanUser, embed.build())

        try {
            val auditLogReason = String.format("Hardbanned by %s - %s", DiscordUtils.getUserTagAndId(user), reason)
            controller.ban(hardbanUser, 7, auditLogReason).complete()
            DiscordUtils.successReact(bot, message)


            val record = transaction {
                JimHardban.new {
                    userid = hardbanUser.id
                    moderatoruserid = user.id
                    guildid = guild.id
                    hardbantime = now.time / 1000
                    this.reason = reason
                }
            }

            val banId = record.id.value
            DiscordUtils.createModLogEntry(bot, shard, message, hardbanUser, reason, "hardban", banId, null, false)
            DiscordUtils.sendMessage(channel, "Hardbanned " + DiscordUtils.getUserTagAndId(hardbanUser))
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not hardban the specified user. Do I have enough permissions?")
        }

        return false
    }
}