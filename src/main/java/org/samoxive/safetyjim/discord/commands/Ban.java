package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.managers.GuildController;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.BanlistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;
import org.samoxive.safetyjim.helpers.Pair;

import java.awt.*;
import java.util.Date;
import java.util.Scanner;

public class Ban extends Command {
    private String[] usages = { "ban @user [reason] | [time] - bans the user with specific arguments. Both arguments can be omitted" };

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

        User banUser = message.getMentionedUsers().get(0);
        Member banMember = guild.getMember(banUser);
        GuildController controller = guild.getController();

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        if (user.getId().equals(banUser.getId())) {
            DiscordUtils.failMessage(bot, message, "You can't ban yourself, dummy!");
            return false;
        }

        if (!DiscordUtils.isBannable(banMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
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
        embed.setTitle("Banned from " + guild.getName());
        embed.setColor(new Color(0x4286F4));
        embed.setDescription("You were banned from " + guild.getName());
        embed.addField("Reason:", reason, false);
        embed.addField("Banned until", expirationDate != null ? expirationDate.toString() : "Indefinitely", false);
        embed.setFooter("Banned by " + DiscordUtils.getUserTagAndId(user), null);
        embed.setTimestamp(now.toInstant());

        DiscordUtils.sendDM(banUser, embed.build());

        try {
            String auditLogReason = String.format("Banned by %s - %s", DiscordUtils.getUserTagAndId(user), reason);
            controller.ban(banMember, 0, auditLogReason).complete();
            DiscordUtils.successReact(bot, message);

            boolean expires = expirationDate != null;
            DSLContext database = bot.getDatabase();

            BanlistRecord record = database.insertInto(Tables.BANLIST,
                                                       Tables.BANLIST.USERID,
                                                       Tables.BANLIST.MODERATORUSERID,
                                                       Tables.BANLIST.GUILDID,
                                                       Tables.BANLIST.BANTIME,
                                                       Tables.BANLIST.EXPIRETIME,
                                                       Tables.BANLIST.REASON,
                                                       Tables.BANLIST.EXPIRES,
                                                       Tables.BANLIST.UNBANNED)
                                           .values(banUser.getId(),
                                                   user.getId(),
                                                   guild.getId(),
                                                   now.getTime() / 1000,
                                                   expirationDate != null ? expirationDate.getTime() / 1000 : 0,
                                                   reason,
                                                   expires,
                                                   false)
                                           .returning(Tables.BANLIST.ID)
                                           .fetchOne();

            int banId = record.getId();
            DiscordUtils.createModLogEntry(bot, shard, message, banMember, reason, "ban", banId, expirationDate, true);
            DiscordUtils.sendMessage(channel, "Banned " + DiscordUtils.getUserTagAndId(banUser));
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Could not ban the specified user. Do I have enough permissions?");
        }

        return false;
    }
}
