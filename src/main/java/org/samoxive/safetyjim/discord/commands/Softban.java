package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.managers.GuildController;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.SoftbanlistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.awt.*;
import java.util.Date;
import java.util.Scanner;

public class Softban extends Command {
    private String[] usages = { "softban @user [reason] | [messages to delete (days)] - softbans the user with the specified args." };

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

        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Ban Members");
            return false;
        }

        if (!messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            return true;
        } else {
            // advance the scanner one step to get rid of user mention
            messageIterator.next();
        }

        User softbanUser = message.getMentionedUsers().get(0);
        Member softbanMember = guild.getMember(softbanUser);
        GuildController controller = guild.getController();

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        if (user.getId().equals(softbanUser.getId())) {
            DiscordUtils.failMessage(bot, message, "You can't softban yourself, dummy!");
            return false;
        }

        if (!DiscordUtils.isBannable(softbanMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        String arguments = TextUtils.seekScannerToEnd(messageIterator);
        String[] argumentsSplit = arguments.split("\\|");
        String reason = argumentsSplit[0];
        reason = reason.equals("") ? "No reason specified" : reason.trim();
        String timeArgument = null;

        if (argumentsSplit.length > 1) {
            timeArgument = argumentsSplit[1];
        }

        int days;

        if (timeArgument != null) {
            try {
                days = Integer.parseInt(timeArgument.trim());
            } catch (NumberFormatException e) {
                DiscordUtils.failMessage(bot, message, "Invalid day count, please try again.");
                return false;
            }
        } else {
            days = 1;
        }

        if (days < 1 || days > 7) {
            DiscordUtils.failMessage(bot, message, "The amount of days must be between 1 and 7.");
            return false;
        }

        Date now = new Date();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Softbanned from " + guild.getName());
        embed.setColor(new Color(0x4286F4));
        embed.setDescription("You were softbanned from " + guild.getName());
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false);
        embed.setFooter("Softbanned by " + DiscordUtils.getUserTagAndId(user), null);
        embed.setTimestamp(now.toInstant());

        DiscordUtils.sendDM(softbanUser, embed.build());

        try {
            String auditLogReason = String.format("Softbanned by %s - %s", DiscordUtils.getUserTagAndId(user), reason);
            controller.ban(softbanMember, days, auditLogReason).complete();
            controller.unban(softbanUser).complete();

            DSLContext database = bot.getDatabase();

            SoftbanlistRecord record = database.insertInto(Tables.SOFTBANLIST,
                                                           Tables.SOFTBANLIST.USERID,
                                                           Tables.SOFTBANLIST.MODERATORUSERID,
                                                           Tables.SOFTBANLIST.GUILDID,
                                                           Tables.SOFTBANLIST.SOFTBANTIME,
                                                           Tables.SOFTBANLIST.DELETEDAYS,
                                                           Tables.SOFTBANLIST.REASON)
                                               .values(softbanUser.getId(),
                                                       user.getId(),
                                                       guild.getId(),
                                                       now.getTime() / 1000,
                                                       days,
                                                       reason)
                                               .returning(Tables.SOFTBANLIST.ID)
                                               .fetchOne();

            DiscordUtils.createModLogEntry(bot, shard, message, softbanMember, reason, "softban", record.getId(), null, false);
            DiscordUtils.sendMessage(channel, "Softbanned " + DiscordUtils.getUserTagAndId(softbanUser));
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Could not softban the specified user. Do I have enough permissions?");
        }

        return false;
    }
}
