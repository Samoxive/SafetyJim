package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimBan
import org.samoxive.safetyjim.database.JimBanTable
import org.samoxive.safetyjim.discord.*
import java.util.*

class Unban : Command() {
    override val usages = arrayOf("unban <tag> - unbans user with specified user tag (example#1998)")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member
        val message = event.message
        val guild = event.guild
        val selfMember = guild.selfMember
        val controller = guild.controller

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Ban Members")
            return false
        }

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I do not have enough permissions to do that!")
            return false
        }

        val (searchResult, targetUser) = messageIterator.findBannedUser(message)
        if (searchResult == SearchUserResult.NOT_FOUND || (targetUser == null)) {
            DiscordUtils.failMessage(bot, message, "Could not find the user to unban!")
            return false
        }

        if (searchResult == SearchUserResult.GUESSED) {
            askConfirmation(bot, message, targetUser)?: return false
        }

        controller.unban(targetUser).complete()

        transaction {
            JimBan.find {
                (JimBanTable.guildid eq guild.id) and (JimBanTable.userid eq targetUser.id)
            }.forUpdate().forEach { it.unbanned = true }
        }

        DiscordUtils.successReact(bot, message)

        return false
    }
}
