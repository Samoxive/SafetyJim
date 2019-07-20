package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.HardbanEntity
import org.samoxive.safetyjim.database.HardbansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

suspend fun hardbanAction(guild: Guild, channel: TextChannel?, settings: SettingsEntity, modUser: User, hardbanUser: User, reason: String) {
    val controller = guild.controller
    val now = Date()

    val embed = EmbedBuilder()
    embed.setTitle("Hardbanned from ${guild.name}")
    embed.setColor(Color(0x4286F4))
    embed.setDescription("You were hardbanned from ${guild.name}")
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.setFooter("Hardbanned by ${modUser.getUserTagAndId()}", null)
    embed.setTimestamp(now.toInstant())

    hardbanUser.trySendMessage(embed.build())

    val auditLogReason = "Hardbanned by ${modUser.getUserTagAndId()} - $reason"
    controller.ban(hardbanUser, 7, auditLogReason).await()

    val record = HardbansTable.insertHardban(
            HardbanEntity(
                    userId = hardbanUser.idLong,
                    moderatorUserId = modUser.idLong,
                    guildId = guild.idLong,
                    hardbanTime = now.time / 1000,
                    reason = reason
            )
    )

    val banId = record.id
    createModLogEntry(guild, channel, settings, modUser, hardbanUser, reason, ModLogAction.Hardban, banId)
}

class Hardban : Command() {
    override val usages = arrayOf("hardban @user [reason] - hard bans the user with specific arguments.")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

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

        try {
            hardbanAction(guild, channel, settings, user, hardbanUser, reason)
            message.successReact()
            channel.sendModActionConfirmationMessage(settings, "Hardbanned ${hardbanUser.getUserTagAndId()}")
        } catch (e: Exception) {
            message.failMessage("Could not hardban the specified user. Do I have enough permissions?")
        }

        return false
    }
}