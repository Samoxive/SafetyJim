package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent;

public abstract class MessageProcessor {
    public boolean onMessage(DiscordBot bot, GuildMessageReceivedEvent event) {
        return false;
    }
    public void onMessageDelete(DiscordBot bot, GuildMessageReactionRemoveEvent event) {}
    public void onReactionAdd(DiscordBot bot, GuildMessageReceivedEvent event) {}
    public void onReactionRemove(DiscordBot bot, GuildMessageReactionRemoveEvent event) {}
}
