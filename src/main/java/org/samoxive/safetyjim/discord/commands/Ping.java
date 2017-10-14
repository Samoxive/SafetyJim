package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

import java.awt.*;

public class Ping extends Command {
    private String[] usages = {"ping - pong"};
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        DiscordUtils.successReact(bot, event.getMessage());
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor("Safety Jim " + event.getJDA().getShardInfo().getShardString(), null, event.getJDA().getSelfUser().getAvatarUrl());
        embed.setDescription(":ping_pong: Ping: " + event.getJDA().getPing() + "ms");
        embed.setColor(new Color(0x4286F4));
        DiscordUtils.sendMessage(event.getChannel(), embed.build());
        return false;
    }
}
