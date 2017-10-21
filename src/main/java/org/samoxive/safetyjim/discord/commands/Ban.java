package org.samoxive.safetyjim.discord.commands;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
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

import java.awt.*;
import java.util.Date;
import java.util.List;
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
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "You don't have enough permissions to execute this command!");
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
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "I don't have enough permissions to do that!");
            return false;
        }

        if (user.getId().equals(banUser.getId())) {
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "You can't ban yourself, dummy!");
            return false;
        }

        if (!DiscordUtils.isBannable(banMember, selfMember)) {
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "I don't have enough permissions to do that!");
            return false;
        }

        String reason;
        String timeArgument;

        if (!messageIterator.hasNext()) {
            reason = "No reason specified";
            timeArgument = null;
        } else {
            StringBuilder argumentsRaw = new StringBuilder();

            while (messageIterator.hasNextLine()) {
                argumentsRaw.append(messageIterator.nextLine());
                argumentsRaw.append("\n");
            }

            String[] splitArgumentsRaw = argumentsRaw.toString().split("\\|");

            if (splitArgumentsRaw.length == 1) {
                reason = splitArgumentsRaw[0];
                timeArgument = null;
            } else {
                reason = splitArgumentsRaw[0];
                timeArgument = splitArgumentsRaw[1];
            }

            reason = reason.trim();
            timeArgument = timeArgument == null ? null : timeArgument.trim();
        }

        Date expirationDate = null;
        Date now = new Date();

        if (timeArgument != null) {
            Parser parser = new Parser();
            List<DateGroup> dateGroups = parser.parse(timeArgument);

            try {
                expirationDate = dateGroups.get(0).getDates().get(0);
            } catch (IndexOutOfBoundsException e) {
                DiscordUtils.failReact(bot, message);
                DiscordUtils.sendMessage(channel, "Invalid time argument `" + timeArgument + "`. Try again");
                return false;
            }

            if (expirationDate.compareTo(now) < 0) {
                DiscordUtils.failReact(bot, message);
                DiscordUtils.sendMessage(channel, "Your time argument was set for the past. Try again.\n" +
                                                           "If you're specifying a date, e.g. `30 December`, make sure you also write the year.");
                return false;
            }
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Banned from " + guild.getName());
        embed.setColor(new Color(0x4286F4));
        embed.setDescription("You were banned from " + guild.getName());
        embed.addField("Reason:", reason, false);
        embed.addField("Banned until", expirationDate != null ? expirationDate.toString() : "Indefinitely", false);
        embed.setFooter("Banned by " + DiscordUtils.getUserTagAndId(user), null);
        embed.setTimestamp(now.toInstant());

        PrivateChannel dmChannel = banUser.openPrivateChannel().complete();
        DiscordUtils.sendMessage(dmChannel, embed.build());

        try {
            String auditLogReason = String.format("Banned by %s (%s) - %s", DiscordUtils.getTag(user), user.getId(), reason);
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
            DiscordUtils.failReact(bot, message);
            DiscordUtils.sendMessage(channel, "Could not ban specified user. Do I have enough permissions?");
        }

        return false;
    }
}
