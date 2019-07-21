package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*

val blacklistedHosts = arrayOf("discord.gg/")

fun isInviteLinkBlacklisted(str: String) = blacklistedHosts.map { str.contains(it) }.filter { it }.any()

class InviteLink : MessageProcessor() {
    override suspend fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent, guildSettings: SettingsEntity): Boolean {
        val message = event.message
        val member = event.member

        if (!guildSettings.inviteLinkRemover) {
            return false
        }

        if (member.isStaff()) {
            return false
        }

        val content = message.contentRaw
        if (!isInviteLinkBlacklisted(content)) {
            return false
        }

        try {
            message.delete().await()
            event.channel.trySendMessage("I'm sorry ${member.asMention}, you can't send invite links here.")
        } catch (e: InsufficientPermissionException) {
            return false
        } catch (e: Exception) {
            return true
        }

        return true
    }
}
