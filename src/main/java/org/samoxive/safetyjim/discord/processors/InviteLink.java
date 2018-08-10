package org.samoxive.safetyjim.discord.processors;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordShard;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.MessageProcessor;

public class InviteLink extends MessageProcessor {
    private String[] blacklistedHosts = { "discord.gg/" };
    // We don't want to censor users that can issue moderative commands
    private Permission[] whitelistedPermissions = {
            Permission.ADMINISTRATOR,
            Permission.BAN_MEMBERS,
            Permission.KICK_MEMBERS,
            Permission.MANAGE_ROLES,
            Permission.MESSAGE_MANAGE,
    };

    @Override
    public boolean onMessage(DiscordBot bot, DiscordShard shard, GuildMessageReceivedEvent event) {
        Message message = event.getMessage();
        Member member = event.getMember();
        for (Permission permission: whitelistedPermissions) {
            if (member.hasPermission(permission)) {
                return false;
            }
        }

        boolean processorEnabled = DatabaseUtils.getGuildSettings(bot, bot.getDatabase(), event.getGuild()).getInvitelinkremover();
        if (!processorEnabled) {
            return false;
        }

        String content = message.getContentRaw();

        boolean inviteLinkExists = false;
        for (String blacklistedHost: blacklistedHosts) {
            if (content.contains(blacklistedHost)) {
                inviteLinkExists = true;
            }
        }

        if (!inviteLinkExists) {
            return false;
        }

        try {
            message.delete().complete();
            DiscordUtils.sendMessage(event.getChannel(), "I'm sorry " + member.getAsMention() + ", you can't send invite links here.");
        } catch (InsufficientPermissionException e) {
            return false;
        } catch (Exception e) {
            return true;
        }

        return true;
    }
}
