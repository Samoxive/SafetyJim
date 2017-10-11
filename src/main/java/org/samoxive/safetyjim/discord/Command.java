package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

public abstract class Command {
    public abstract boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args);
}
