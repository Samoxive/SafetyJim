package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent

abstract class MessageProcessor {
    open fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent): Boolean {
        return false
    }

    open fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {}
    fun onReactionAdd(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReactionAddEvent) {}
    fun onReactionRemove(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReactionRemoveEvent) {}
}
