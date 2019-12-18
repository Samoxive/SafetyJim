package org.samoxive.safetyjim.discord.commands

import java.util.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.MutesTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*

class Unmute : Command() {
    override val usages = arrayOf("unmute @user - unmutes specified user")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)
        val member = event.member!!
        val message = event.message
        val guild = event.guild

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Manage Roles")
            return false
        }

        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            message.failMessage("I don't have enough permissions do this action!")
            return false
        }

        if (args.isEmpty()) {
            return true
        }

        val mutedRoles = guild.getRolesByName("Muted", false)
        if (mutedRoles.size == 0) {
            message.failMessage("Could not find a role called Muted, please create one yourself or mute a user to set it up automatically.")
            return false
        }

        val (searchResult, unmuteUser) = messageIterator.findUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (unmuteUser == null)) {
            message.failMessage("Could not find the user to unmute!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, unmuteUser) ?: return false
        }

        val muteRole = mutedRoles[0]
        val unmuteMember = guild.fetchMember(unmuteUser)!!
        try {
            guild.removeRoleFromMember(unmuteMember, muteRole).await()
        } catch (e: Exception) {
            message.failMessage("Could not unmute the user: \"${unmuteUser.name}\". Do I have enough permissions or is Muted role below me?")
            return false
        }

        MutesTable.invalidatePreviousUserMutes(guild, unmuteUser)
        message.successReact()
        return false
    }
}
