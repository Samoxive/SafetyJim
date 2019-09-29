package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.discord.commands.*
import org.samoxive.safetyjim.tryhardAsync
import java.time.Instant
import java.util.*

private val blacklistedHosts = arrayOf("discord.gg/")
private const val ACTION_REASON = "Sending invite links"

fun isInviteLinkBlacklisted(str: String) = blacklistedHosts.map { str.contains(it) }.filter { it }.any()

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
        if (!isInviteLinkBlacklisted(content)) {
            return false
        }

        val expirationDate = if (settings.inviteLinkRemoverAction == SettingsEntity.ACTION_BAN || settings.inviteLinkRemoverAction == SettingsEntity.ACTION_MUTE) {
            if (settings.inviteLinkRemoverActionDuration == 0) {
                null
            } else {
                Date.from(Instant.now().plusSeconds(settings.getInviteLinkRemoverActionDurationDelta().toLong()))
            }
        } else {
            null
        }

        tryhardAsync {
            message.delete().await()
            when (settings.inviteLinkRemoverAction) {
                SettingsEntity.ACTION_NOTHING -> {
                }
                SettingsEntity.ACTION_WARN -> warnAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                SettingsEntity.ACTION_MUTE -> muteAction(guild, channel, settings, selfUser, targetUser, null, ACTION_REASON, expirationDate)
                SettingsEntity.ACTION_KICK -> kickAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                SettingsEntity.ACTION_BAN -> banAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON, expirationDate)
                SettingsEntity.ACTION_SOFTBAN -> softbanAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                SettingsEntity.ACTION_HARDBAN -> hardbanAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                else -> throw IllegalStateException()
            }
        }

        return true
    }
}
