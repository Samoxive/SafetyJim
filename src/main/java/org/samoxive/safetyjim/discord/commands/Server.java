package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.StringJoiner;

public class Server extends Command {
    private String[] usages = { "server - displays information about the current server" };

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Guild guild = event.getGuild();
        User owner = guild.getOwner().getUser();
        TextChannel channel = event.getChannel();
        Message message = event.getMessage();
        String memberCount = Long.toString(guild.getMemberCache().size());
        String creationDate = guild.getCreationTime().toLocalDate().toString();
        StringBuilder emojis = new StringBuilder();

        for (Emote emote: guild.getEmotes()) {
            emojis.append(emote.getAsMention());
        }

        String emojiString = emojis.toString();
        emojiString = emojiString.equals("") ? "None" : emojiString;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(guild.getName(), null, guild.getIconUrl());
        embed.setColor(new Color(0x4286F4));
        embed.addField("Server Owner", DiscordUtils.getTag(owner), true);
        embed.addField("Member Count", memberCount, true);
        embed.addField("Creation Date", creationDate, true);
        embed.addField("Emojis", emojiString, false);

        DiscordUtils.successReact(bot, message);
        DiscordUtils.sendMessage(channel, embed.build());

        return false;
    }
}
