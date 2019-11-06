package org.samoxive.safetyjim.discord.commands

import java.util.*
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.BansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*

class Unban : Command() {
    override val usages = arrayOf("unban @user - unbans specified user")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member!!
        val message = event.message
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Ban Members")
            return false
        }

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            message.failMessage("I do not have enough permissions to do that!")
            return false
        }

        if (args.isEmpty()) {
            return true
        }

        val (searchResult, targetUser) = messageIterator.findBannedUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (targetUser == null)) {
            message.failMessage("Could not find the user to unban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            message.askConfirmation(bot, targetUser) ?: return false
        }

        guild.unban(targetUser).await()
        BansTable.invalidatePreviousUserBans(guild, targetUser)
        message.successReact()

        return false
    }
}
