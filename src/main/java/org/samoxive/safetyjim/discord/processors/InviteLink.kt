package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordShard
import org.samoxive.safetyjim.discord.MessageProcessor
import org.samoxive.safetyjim.discord.trySendMessage

val blacklistedHosts = arrayOf("discord.gg/")
// We don't want to censor users that can issue moderative commands
val whitelistedPermissions = arrayOf(Permission.ADMINISTRATOR, Permission.BAN_MEMBERS, Permission.KICK_MEMBERS, Permission.MANAGE_ROLES, Permission.MESSAGE_MANAGE)

fun isInviteLinkBlacklisted(str: String) = blacklistedHosts.map { str.contains(it) }.filter { it }.any()

class InviteLink : MessageProcessor() {
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
        if (!isInviteLinkBlacklisted(content)) {
            return false
        }

        try {
            message.delete().complete()
            event.channel.trySendMessage("I'm sorry ${member.asMention}, you can't send invite links here.")
        } catch (e: InsufficientPermissionException) {
            return false
        } catch (e: Exception) {
            return true
        }

        return true
    }
}
