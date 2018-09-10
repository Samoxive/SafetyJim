package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordShard
import org.samoxive.safetyjim.discord.MessageProcessor

class InviteLink : MessageProcessor() {
    private val blacklistedHosts = arrayOf("discord.gg/")
    // We don't want to censor users that can issue moderative commands
    private val whitelistedPermissions = arrayOf(Permission.ADMINISTRATOR, Permission.BAN_MEMBERS, Permission.KICK_MEMBERS, Permission.MANAGE_ROLES, Permission.MESSAGE_MANAGE)

    override fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent): Boolean {
        val message = event.message
        val member = event.member
        for (permission in whitelistedPermissions) {
            if (member.hasPermission(permission)) {
                return false
            }
        }

        val processorEnabled = getGuildSettings(event.guild, bot.config).invitelinkremover
        if (!processorEnabled) {
            return false
        }

        val content = message.contentRaw

        var inviteLinkExists = false
        for (blacklistedHost in blacklistedHosts) {
            if (content.contains(blacklistedHost)) {
                inviteLinkExists = true
            }
        }

        if (!inviteLinkExists) {
            return false
        }

        try {
            message.delete().complete()
            event.channel.sendMessage("I'm sorry " + member.asMention + ", you can't send invite links here.")
        } catch (e: InsufficientPermissionException) {
            return false
        } catch (e: Exception) {
            return true
        }

        return true
    }
}
