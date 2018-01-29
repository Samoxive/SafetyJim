package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.WarnlistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.awt.*;
import java.util.Date;
import java.util.Scanner;

public class Warn extends Command {
    private String[] usages = { "warn @user [reason] - warn the user with the specified reason" };

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

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command!");
            return false;
        }

        if (!messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            return true;
        } else {
            // advance the scanner one step to get rid of user mention
            messageIterator.next();
        }

        User warnUser = message.getMentionedUsers().get(0);
        Member warnMember = guild.getMember(warnUser);

        if (user.getId().equals(warnUser.getId())) {
            DiscordUtils.failMessage(bot, message, "You can't warn yourself, dummy!");
            return false;
        }

        String reason = TextUtils.seekScannerToEnd(messageIterator);
        reason = reason.equals("") ? "No reason specified" : reason;

        Date now = new Date();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Warned in " + guild.getName());
        embed.setColor(new Color(0x4286F4));
        embed.setDescription("You were warned in " + guild.getName());
        embed.addField("Reason:", TextUtils.truncateForEmbed(reason), false);
        embed.setFooter("Warned by " + DiscordUtils.getUserTagAndId(user), null);
        embed.setTimestamp(now.toInstant());

        try {
            DiscordUtils.sendDM(warnUser, embed.build());
        } catch (Exception e) {
            DiscordUtils.sendMessage(channel, "Could not send a warning to the specified user via private message!");
        }

        DiscordUtils.successReact(bot, message);

        DSLContext database = bot.getDatabase();

        WarnlistRecord record = database.insertInto(Tables.WARNLIST,
                Tables.WARNLIST.USERID,
                Tables.WARNLIST.MODERATORUSERID,
                Tables.WARNLIST.GUILDID,
                Tables.WARNLIST.WARNTIME,
                Tables.WARNLIST.REASON)
                .values(warnUser.getId(),
                        user.getId(),
                        guild.getId(),
                        now.getTime() / 1000,
                        reason)
                .returning(Tables.WARNLIST.ID)
                .fetchOne();

        DiscordUtils.createModLogEntry(bot, shard, message, warnMember, reason, "warn", record.getId(), null, false);
        DiscordUtils.sendMessage(channel, "Warned " + DiscordUtils.getUserTagAndId(warnUser));

        return false;
    }
}
