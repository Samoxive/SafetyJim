package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent;

public abstract class MessageProcessor {
    public boolean onMessage(DiscordBot bot, DiscordShard shard, GuildMessageReceivedEvent event) {
        return false;
    }
    public void onMessageDelete(DiscordBot bot, DiscordShard shard, GuildMessageDeleteEvent event) {}
    public void onReactionAdd(DiscordBot bot, DiscordShard shard, GuildMessageReactionAddEvent event) {}
    public void onReactionRemove(DiscordBot bot, DiscordShard shard, GuildMessageReactionRemoveEvent event) {}
}
