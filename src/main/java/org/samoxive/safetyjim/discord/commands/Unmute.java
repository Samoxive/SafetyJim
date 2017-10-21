package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.managers.GuildController;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.MutelistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;

import java.util.List;

public class Unmute extends Command {
    private String[] usages = { "unmute @user1 @user2 ... - unmutes specified user" };

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        String[] splitArgs = args.split(" ");
        Member member = event.getMember();
        Message message = event.getMessage();
        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel();
        GuildController controller = guild.getController();
        List<User> mentions = message.getMentionedUsers();

        DSLContext database = bot.getDatabase();

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "You don't have enough permissions to execute this command!");
            return false;
        }

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "I don't have enough permissions do this action!");
            return false;
        }

        // If no arguments are given or there are no mentions or first word isn't a user mention, display syntax text
        if (args.equals("") ||
            message.getMentionedUsers().size() == 0) {
            return true;
        }

        Role muteRole = guild.getRolesByName("Muted", false).get(0);

        if (muteRole == null) {
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "Could not find a role called Muted, please create one yourself or mute a user to set it up automatically.");
            return false;
        }

        for (User unmuteUser: mentions) {
            Member unmuteMember = guild.getMember(unmuteUser);
            controller.removeSingleRoleFromMember(unmuteMember, muteRole).queue();
            MutelistRecord record = database.selectFrom(Tables.MUTELIST)
                    .where(Tables.MUTELIST.USERID.eq(unmuteUser.getId()))
                    .and(Tables.MUTELIST.GUILDID.eq(guild.getId()))
                    .fetchOne();

            if (record == null) {
                continue;
            }

            record.setUnmuted(true);
            record.update();
        }

        DiscordUtils.successReact(bot, message);
        return false;
    }
}
