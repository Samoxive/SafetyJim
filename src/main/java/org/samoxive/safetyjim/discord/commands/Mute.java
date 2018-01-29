package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
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
import org.samoxive.safetyjim.discord.TextUtils;
import org.samoxive.safetyjim.helpers.Pair;

import java.awt.Color;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class Mute extends Command {
    private String[] usages = { "mute @user [reason] | [time] - mutes the user with specific args. Both arguments can be omitted." };

    public static Role setupMutedRole(Guild guild) {
        GuildController controller = guild.getController();
        List<TextChannel> channels = guild.getTextChannels();
        List<Role> roleList = guild.getRoles();
        Role mutedRole = null;

        for (Role role: roleList) {
            if (role.getName().equals("Muted")) {
                mutedRole = role;
                break;
            }
        }

        if (mutedRole == null) {
            // Muted role doesn't exist at all, so we need to create one
            // and create channel overrides for the role
            mutedRole = controller.createRole()
                                  .setName("Muted")
                                  .setPermissions(
                                          Permission.MESSAGE_READ,
                                          Permission.MESSAGE_HISTORY,
                                          Permission.VOICE_CONNECT
                                  )
                                  .complete();

            for (TextChannel channel: channels) {
                channel.createPermissionOverride(mutedRole)
                        .setDeny(
                                Permission.MESSAGE_WRITE,
                                Permission.MESSAGE_ADD_REACTION,
                                Permission.VOICE_SPEAK
                        )
                        .complete();
            }
        }

        for (TextChannel channel: channels) {
            PermissionOverride override = null;
            for (PermissionOverride channelOverride: channel.getRolePermissionOverrides()) {
                if (channelOverride.getRole().equals(mutedRole)) {
                    override = channelOverride;
                    break;
                }
            }

            // This channel is either created after we created a Muted role
            // or its permissions were played with, so we should set it straight
            if (override == null) {
                channel.createPermissionOverride(mutedRole)
                        .setDeny(
                                Permission.MESSAGE_WRITE,
                                Permission.MESSAGE_ADD_REACTION,
                                Permission.VOICE_SPEAK
                        )
                        .complete();
            }
        }

        // return the found or created muted role so command can use it
        return mutedRole;
    }

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Scanner messageIterator = new Scanner(args);
        JDA shard = event.getJDA();

        Member member = event.getMember();
        User user = event.getAuthor();
        Message message = event.getMessage();
        TextChannel channel = event.getChannel();
        Guild guild = event.getGuild();
        Member selfMember = guild.getSelfMember();

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Manage Roles");
            return false;
        }

        if (!messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            return true;
        } else {
            // advance the scanner one step to get rid of user mention
            messageIterator.next();
        }

        User muteUser = message.getMentionedUsers().get(0);
        Member muteMember = guild.getMember(muteUser);
        GuildController controller = guild.getController();

        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        if (user.getId().equals(muteUser.getId())) {
            DiscordUtils.failMessage(bot, message, "You can't mute yourself, dummy!");
            return false;
        }

        if (user.getId().equals(selfMember.getUser().getId())) {
            DiscordUtils.failMessage(bot, message, "Now that's just rude. (I can't mute myself)");
            return false;
        }

        Role mutedRole = null;
        try {
            mutedRole = setupMutedRole(guild);
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Could not create a Muted role, do I have enough permissions?");
            return false;
        }

        Pair<String, Date> parsedReasonAndTime;

        try {
            parsedReasonAndTime = TextUtils.getTextAndTime(messageIterator);
        } catch (TextUtils.InvalidTimeInputException e) {
            DiscordUtils.failMessage(bot, message, "Invalid time argument. Please try again.");
            return false;
        } catch (TextUtils.TimeInputInPastException e) {
            DiscordUtils.failMessage(bot, message, "Your time argument was set for the past. Try again.\n" +
                    "If you're specifying a date, e.g. `30 December`, make sure you also write the year.");
            return false;
        }

        String text = parsedReasonAndTime.getLeft();
        String reason = text == null || text.equals("") ? "No reason specified" : text;
        Date expirationDate = parsedReasonAndTime.getRight();
        Date now = new Date();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Muted in " + guild.getName());
        embed.setColor(new Color(0x4286F4));
        embed.setDescription("You were muted in " + guild.getName());
        embed.addField("Reason:", reason, false);
        embed.addField("Muted until", expirationDate != null ? expirationDate.toString() : "Indefinitely", false);
        embed.setFooter("Muted by " + DiscordUtils.getUserTagAndId(user), null);
        embed.setTimestamp(now.toInstant());

        DiscordUtils.sendDM(muteUser, embed.build());

        try {
            controller.addSingleRoleToMember(muteMember, mutedRole).complete();
            DiscordUtils.successReact(bot, message);

            boolean expires = expirationDate != null;
            DSLContext database = bot.getDatabase();

            database.update(Tables.MUTELIST)
                    .set(Tables.MUTELIST.UNMUTED, true)
                    .where(Tables.MUTELIST.GUILDID.eq(guild.getId()))
                    .and(Tables.MUTELIST.USERID.eq(muteUser.getId()))
                    .execute();

            MutelistRecord record = database.insertInto(Tables.MUTELIST,
                                                        Tables.MUTELIST.USERID,
                                                        Tables.MUTELIST.MODERATORUSERID,
                                                        Tables.MUTELIST.GUILDID,
                                                        Tables.MUTELIST.MUTETIME,
                                                        Tables.MUTELIST.EXPIRETIME,
                                                        Tables.MUTELIST.REASON,
                                                        Tables.MUTELIST.EXPIRES,
                                                        Tables.MUTELIST.UNMUTED)
                                            .values(muteUser.getId(),
                                                    user.getId(),
                                                    guild.getId(),
                                                    now.getTime() / 1000,
                                                    expirationDate == null ? 0 : expirationDate.getTime() / 1000,
                                                    reason,
                                                    expires,
                                                    false)
                                            .returning(Tables.MUTELIST.ID)
                                            .fetchOne();
            DiscordUtils.createModLogEntry(bot, shard, message, muteMember, reason, "mute", record.getId(), expirationDate, true);
            DiscordUtils.sendMessage(channel, "Muted " + DiscordUtils.getUserTagAndId(muteUser));
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Could not mute the specified user. Do I have enough permissions?");
        }

        return false;
    }
}
