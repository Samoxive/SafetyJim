package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimMute
import org.samoxive.safetyjim.database.JimMuteTable
import org.samoxive.safetyjim.discord.*
import java.util.*

class Unmute : Command() {
    override val usages = arrayOf("unmute @user1 @user2 ... - unmutes specified user")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)
        val member = event.member
        val message = event.message
        val guild = event.guild
        val controller = guild.controller

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Manage Roles")
            return false
        }

        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions do this action!")
            return false
        }

        val mutedRoles = guild.getRolesByName("Muted", false)
        if (mutedRoles.size == 0) {
            DiscordUtils.failMessage(bot, message, "Could not find a role called Muted, please create one yourself or mute a user to set it up automatically.")
            return false
        }

        val (searchResult, unmuteUser) = messageIterator.findUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (unmuteUser == null)) {
            DiscordUtils.failMessage(bot, message, "Could not find the user to unmute!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            askConfirmation(bot, message, unmuteUser) ?: return false
        }

        val muteRole = mutedRoles[0]
        val unmuteMember = guild.getMember(unmuteUser)
        try {
            controller.removeSingleRoleFromMember(unmuteMember, muteRole).complete()
        } catch (e: Exception) {
            DiscordUtils.failMessage(bot, message, "Could not unmute the user: \"" + unmuteUser.name + "\". Do I have enough permissions or is Muted role below me?")
            return false
        }

        transaction {
            JimMute.find {
                (JimMuteTable.guildid eq guild.id) and (JimMuteTable.userid eq unmuteUser.id)
            }.forUpdate().forEach { it.unmuted = true }
        }

        DiscordUtils.successReact(bot, message)
        return false
    }
}
