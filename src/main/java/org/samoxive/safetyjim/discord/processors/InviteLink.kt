package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhardAsync

private val blocklistedHosts = arrayOf("discord.gg/")
private const val ACTION_REASON = "Sending invite links"

fun isInviteLinkBlocklisted(str: String) = blocklistedHosts.map { str.contains(it) }.any { it }

class InviteLink : MessageProcessor() {
    override suspend fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent, settings: SettingsEntity): Boolean {
        val message = event.message
        val member = event.member!!
        val channel = event.channel
        val guild = event.guild
        val selfUser = event.jda.selfUser
        val targetUser = event.author

        if (!settings.inviteLinkRemover) {
            return false
        }

        if (member.isStaff()) {
            return false
        }

        val content = message.contentRaw
        if (!isInviteLinkBlocklisted(content)) {
            return false
        }

        val expirationDate = settings.getInviteLinkRemoverActionExpirationDate()
        tryhardAsync {
            message.delete().await()
            executeModAction(settings.inviteLinkRemoverAction, guild, channel, settings, selfUser, targetUser, ACTION_REASON, expirationDate)
        }

        return true
    }
}
