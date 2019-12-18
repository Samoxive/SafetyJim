package org.samoxive.safetyjim.discord.commands

import java.awt.Color
import java.util.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.BanEntity
import org.samoxive.safetyjim.database.BansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*

suspend fun banAction(guild: Guild, channel: TextChannel?, settings: SettingsEntity, modUser: User, banUser: User, reason: String, expirationDate: Date?) {
    val now = Date()
    val embed = EmbedBuilder()
    embed.setTitle("Banned from ${guild.name}")
    embed.setColor(Color(0x4286F4))
    embed.setDescription("You were banned from ${guild.name}")
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.addField("Banned until", expirationDate?.toString() ?: "Indefinitely", false)
    embed.setFooter("Banned by ${modUser.getUserTagAndId()}", null)
    embed.setTimestamp(now.toInstant())

    banUser.trySendMessage(embed.build())

    val auditLogReason = "Banned by ${modUser.getUserTagAndId()} - $reason"
    guild.ban(banUser, 0, auditLogReason).await()

    val expires = expirationDate != null

    BansTable.invalidatePreviousUserBans(guild, banUser)
    val record = BansTable.insertBan(
        BanEntity(
            userId = banUser.idLong,
            moderatorUserId = modUser.idLong,
            guildId = guild.idLong,
            banTime = now.time / 1000,
            expireTime = if (expirationDate != null) expirationDate.time / 1000 else 0,
            reason = reason,
            expires = expires,
            unbanned = false
        )
    )

    val banId = record.id
    createModLogEntry(guild, channel, settings, modUser, banUser, reason, ModLogAction.Ban, banId, expirationDate)
}

class Ban : Command() {
    override val usages = arrayOf("ban @user [reason] | [time] - bans the user with specific arguments. Both arguments can be omitted")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member!!
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

        val (searchResult, banUser) = messageIterator.findUser(message, true)
        if (searchResult == SearchUserResult.NOT_FOUND || (banUser == null)) {
            message.failMessage("Could not find the user to ban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, banUser) ?: return false
        }

        val banMember = guild.getMember(banUser)

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        if (user == banUser) {
            message.failMessage("You can't ban yourself, dummy!")
            return false
        }

        if (banMember != null && !banMember.isBannableBy(selfMember)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        val parsedReasonAndTime = try {
            messageIterator.getTextAndTime()
        } catch (e: InvalidTimeInputException) {
            message.failMessage("Invalid time argument. Please try again.")
            return false
        } catch (e: TimeInputInPastException) {
            message.failMessage("Your time argument was set for the past. Try again.\nIf you're specifying a date, e.g. `30 December`, make sure you also write the year.")
            return false
        }

        val (text, expirationDate) = parsedReasonAndTime
        val reason = if (text == "") "No reason specified" else text

        try {
            banAction(guild, channel, settings, user, banUser, reason, expirationDate)
            message.successReact()
            channel.sendModActionConfirmationMessage(settings, "Banned ${banUser.getUserTagAndId()} ${getExpirationTextInChannel(expirationDate)}")
        } catch (e: Exception) {
            message.failMessage("Could not ban the specified user. Do I have enough permissions?")
        }

        return false
    }
}
