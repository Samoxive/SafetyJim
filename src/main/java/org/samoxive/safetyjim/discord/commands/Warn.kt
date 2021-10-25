package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.WarnEntity
import org.samoxive.safetyjim.database.WarnsTable
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

private const val ACTION_REASON = "Warning threshold exceeded."

suspend fun warnAction(guild: Guild, channel: TextChannel?, settings: SettingsEntity, modUser: User, warnUser: User, reason: String, callDepth: Int = 0) {
    val now = Date()

    val embed = EmbedBuilder()
    embed.setTitle("Warned in ${guild.name}")
    embed.setColor(Color(0x4286F4))
    embed.setDescription("You were warned in ${guild.name}")
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.setFooter("Warned by ${modUser.getUserTagAndId()}", null)
    embed.setTimestamp(now.toInstant())

    warnUser.trySendMessage(embed.build())
    val record = WarnsTable.insertWarn(
        WarnEntity(
            userId = warnUser.idLong,
            moderatorUserId = modUser.idLong,
            guildId = guild.idLong,
            warnTime = now.time / 1000,
            reason = reason,
            pardoned = false
        )
    )

    createModLogEntry(guild, channel, settings, modUser, warnUser, reason, ModLogAction.Warn, record.id)

    if (settings.warnThreshold != 0) {
        val warnCount = WarnsTable.fetchUserActionableWarnCount(guild, warnUser)
        if (warnCount >= settings.warnThreshold) {
            val expirationDate = settings.getWarnActionExpirationDate()
            executeModAction(settings.warnAction, guild, channel, settings, modUser, warnUser, ACTION_REASON, expirationDate, callDepth)
        }
    }
}

class Warn : Command() {
    override val usages = arrayOf("warn @user [reason] - warn the user with the specified reason")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member!!
        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            message.failMessage("You don't have enough permissions to execute this command!")
            return false
        }

        if (args.isEmpty()) {
            return true
        }

        val (searchResult, warnUser) = messageIterator.findUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (warnUser == null)) {
            message.failMessage("Could not find the user to warn!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, warnUser) ?: return false
        }

        if (user == warnUser) {
            message.failMessage("You can't warn yourself, dummy!")
            return false
        }

        var reason = messageIterator.seekToEnd()
        reason = if (reason == "") "No reason specified" else reason

        warnAction(guild, channel, settings, user, warnUser, reason)
        message.successReact()
        channel.sendModActionConfirmationMessage(settings, "Warned ${warnUser.getUserTagAndId()}")

        return false
    }
}
