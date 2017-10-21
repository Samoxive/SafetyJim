package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

import java.awt.*;

public class Invite extends Command {
    private String[] usages = { "invite - provides the invite link for Jim" };
    private EmbedBuilder embedBuilder;
    private MessageEmbed embed;
    private boolean embedHasAvatarURL = false;
    private final String botLink = "https://discordapp.com/oauth2/authorize?client_id=313749262687141888&permissions=268446790&scope=bot";
    private final String inviteLink = "https://discord.io/safetyjim";
    /*
    * private embed = {
        author: {
            name: `Safety Jim`,
            icon_url: undefined,
        },
        fields: [
            { name: 'Invite Jim!', value: `[Here](${botLink})`, inline: true },
            { name: 'Join our support server!', value: `[Here](${inviteLink})`, inline: true },
        ],
        color: 0x4286f4,
    };
    * */

    public Invite() {
        embedBuilder = new EmbedBuilder();
        embedBuilder.addField("Invite Jim!", String.format("[Here](%s)", botLink), true);
        embedBuilder.addField("Join our support server!", String.format("[Here](%s)", inviteLink), true);
        embedBuilder.setColor(new Color(0x4286F4));
    }

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Message message = event.getMessage();
        TextChannel channel = event.getChannel();
        JDA shard = event.getJDA();

        if (!embedHasAvatarURL) {
            embedBuilder.setAuthor("Safety Jim", null, shard.getSelfUser().getAvatarUrl());
            embed = embedBuilder.build();
            embedHasAvatarURL = true;
        }

        DiscordUtils.successReact(bot, message);
        DiscordUtils.sendMessage(channel, embed);

        return false;
    }
}
