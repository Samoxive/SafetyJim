package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent

abstract class MessageProcessor {
    open suspend fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent): Boolean {
        return false
    }

    open suspend fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {}
    suspend fun onReactionAdd(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReactionAddEvent) {}
    suspend fun onReactionRemove(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReactionRemoveEvent) {}
}
