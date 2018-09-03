package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimMute
import org.samoxive.safetyjim.database.JimMuteTable
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils

class Unmute : Command() {
    override val usages = arrayOf("unmute @user1 @user2 ... - unmutes specified user")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val member = event.member
        val message = event.message
        val guild = event.guild
        val controller = guild.controller
        val mentions = message.mentionedUsers

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Manage Roles")
            return false
        }

        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions do this action!")
            return false
        }

        // If no arguments are given or there are no mentions or first word isn't a user mention, display syntax text
        if (args == "" || mentions.size == 0) {
            return true
        }

        val mutedRoles = guild.getRolesByName("Muted", false)
        if (mutedRoles.size == 0) {
            DiscordUtils.failMessage(bot, message, "Could not find a role called Muted, please create one yourself or mute a user to set it up automatically.")
            return false
        }

        val muteRole = mutedRoles[0]
        for (unmuteUser in mentions) {
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
        }

        DiscordUtils.successReact(bot, message)
        return false
    }
}