package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.KickEntity
import org.samoxive.safetyjim.database.KicksTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

suspend fun kickAction(guild: Guild, channel: TextChannel?, settings: SettingsEntity, modUser: User, kickUser: User, reason: String) {
    val now = Date()

    val embed = EmbedBuilder()
    embed.setTitle("Kicked from ${guild.name}")
    embed.setColor(Color(0x4286F4))
    embed.setDescription("You were kicked from ${guild.name}")
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.setFooter("Kicked by ${modUser.getUserTagAndId()}", null)
    embed.setTimestamp(now.toInstant())

    kickUser.trySendMessage(embed.build())

    val auditLogReason = "Kicked by ${modUser.getUserTagAndId()} - $reason"
    guild.kick(kickUser.id, auditLogReason).await()

    val record = KicksTable.insertKick(
            KickEntity(
                    userId = kickUser.idLong,
                    moderatorUserId = modUser.idLong,
                    guildId = guild.idLong,
                    kickTime = now.time / 1000,
                    reason = reason
            )
    )

    createModLogEntry(guild, channel, settings, modUser, kickUser, reason, ModLogAction.Kick, record.id)
}

class Kick : Command() {
    override val usages = arrayOf("kick @user [reason] - kicks the user with the specified reason")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member!!
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Kick Members")
            return false
        }

        if (args.isEmpty()) {
            return true
        }

        val (searchResult, kickUser) = messageIterator.findUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (kickUser == null)) {
            message.failMessage("Could not find the user to kick!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, kickUser) ?: return false
        }

        val kickMember = guild.getMember(kickUser)

        if (!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        if (user == kickUser) {
            message.failMessage("You can't kick yourself, dummy!")
            return false
        }

        if (kickMember != null && !kickMember.isKickableBy(selfMember)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        var reason = messageIterator.seekToEnd()
        reason = if (reason == "") "No reason specified" else reason

        try {
            kickAction(guild, channel, settings, user, kickUser, reason)
            message.successReact()
            channel.sendModActionConfirmationMessage(settings, "Kicked ${kickUser.getUserTagAndId()}")
        } catch (e: Exception) {
            message.failMessage("Could not kick the specified user. Do I have enough permissions?")
        }

        return false
    }
}
