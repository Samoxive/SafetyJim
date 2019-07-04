package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.HardbanEntity
import org.samoxive.safetyjim.database.HardbansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

class Hardban : Command() {
    override val usages = arrayOf("hardban @user [reason] - hard bans the user with specific arguments. Both arguments can be omitted.")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)
        val shard = event.jda

        val member = event.member
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Ban Members")
            return false
        }

        if (args.isEmpty()) {
            return true
        }

        val (searchResult, hardbanUser) = messageIterator.findUser(message, true)
        if (searchResult == SearchUserResult.NOT_FOUND || (hardbanUser == null)) {
            message.failMessage("Could not find the user to hardban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, hardbanUser) ?: return false
        }

        val hardbanMember = guild.getMember(hardbanUser)
        val controller = guild.controller

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        if (user == hardbanUser) {
            message.failMessage("You can't hardban yourself, dummy!")
            return false
        }

        if (hardbanMember != null && !hardbanMember.isBannableBy(selfMember)) {
            message.failMessage("I don't have enough permissions to do that!")
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
        embed.setFooter("Hardbanned by " + user.getUserTagAndId(), null)
        embed.setTimestamp(now.toInstant())

        hardbanUser.trySendMessage(embed.build())

        try {
            val auditLogReason = "Hardbanned by ${user.getUserTagAndId()} - $reason"
            controller.ban(hardbanUser, 7, auditLogReason).await()
            message.successReact()

            val record = HardbansTable.insertHardban(
                    HardbanEntity(
                            userId = hardbanUser.idLong,
                            moderatorUserId = user.idLong,
                            guildId = guild.idLong,
                            hardbanTime = now.time / 1000,
                            reason = reason
                    )
            )

            val banId = record.id
            message.createModLogEntry(shard, settings, hardbanUser, reason, "hardban", banId, null, false)
            channel.sendModActionConfirmationMessage(settings, "Hardbanned ${hardbanUser.getUserTagAndId()}")
        } catch (e: Exception) {
            message.failMessage("Could not hardban the specified user. Do I have enough permissions?")
        }

        return false
    }
}