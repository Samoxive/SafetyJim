package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SoftbanEntity
import org.samoxive.safetyjim.database.SoftbansTable
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

suspend fun softbanAction(guild: Guild, channel: TextChannel?, settings: SettingsEntity, modUser: User, softbanUser: User, reason: String) {
    val controller = guild.controller
    val now = Date()

    val embed = EmbedBuilder()
    embed.setTitle("Softbanned from ${guild.name}")
    embed.setColor(Color(0x4286F4))
    embed.setDescription("You were softbanned from ${guild.name}")
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.setFooter("Softbanned by ${modUser.getUserTagAndId()}", null)
    embed.setTimestamp(now.toInstant())

    softbanUser.trySendMessage(embed.build())
    val auditLogReason = "Softbanned by ${modUser.getUserTagAndId()} - $reason"
    controller.ban(softbanUser, 1, auditLogReason).await()
    controller.unban(softbanUser).await()

    val record = SoftbansTable.insertSoftban(
            SoftbanEntity(
                    userId = softbanUser.idLong,
                    moderatorUserId = modUser.idLong,
                    guildId = guild.idLong,
                    softbanTime = now.time / 1000,
                    reason = reason
            )
    )

    createModLogEntry(guild, channel, settings, modUser, softbanUser, reason, ModLogAction.Softban, record.id)
}

class Softban : Command() {
    override val usages = arrayOf("softban @user [reason] - soft bans the user with the specified arguments.")

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

        val (searchResult, softbanUser) = messageIterator.findUser(message, isForBan = true)
        if (searchResult == SearchUserResult.NOT_FOUND || (softbanUser == null)) {
            message.failMessage("Could not find the user to softban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, softbanUser) ?: return false
        }

        val softbanMember = guild.getMember(softbanUser)

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        if (user == softbanUser) {
            message.failMessage("You can't softban yourself, dummy!")
            return false
        }

        if (softbanMember != null && !softbanMember.isBannableBy(selfMember)) {
            message.failMessage("I don't have enough permissions to do that!")
            return false
        }

        var reason = messageIterator.seekToEnd()
        reason = if (reason == "") "No reason specified" else reason

        try {
            softbanAction(guild, channel, settings, user, softbanUser, reason)
            message.successReact()
            channel.sendModActionConfirmationMessage(settings, "Softbanned ${softbanUser.getUserTagAndId()}")
        } catch (e: Exception) {
            message.failMessage("Could not softban the specified user. Do I have enough permissions?")
        }

        return false
    }
}
