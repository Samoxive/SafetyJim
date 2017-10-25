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
import org.samoxive.jooq.generated.tables.records.KicklistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.awt.*;
import java.util.Date;
import java.util.Scanner;

public class Kick extends Command {
    private String[] usages = { "kick @user [reason] - kicks the user with the specified reason" };
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

        User kickUser = message.getMentionedUsers().get(0);
        Member kickMember = guild.getMember(kickUser);
        GuildController controller = guild.getController();

        if (!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        if (user.getId().equals(kickUser.getId())) {
            DiscordUtils.failMessage(bot, message, "You can't kick yourself, dummy!");
            return false;
        }

        if (!DiscordUtils.isKickable(kickMember, selfMember)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that!");
            return false;
        }

        String reason = TextUtils.seekScannerToEnd(messageIterator);
        reason = reason.equals("") ? "No reason specified" : reason;

        Date now = new Date();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Kicked from " + guild.getName());
        embed.setColor(new Color(0x4286F4));
        embed.setDescription("You were kicked from " + guild.getName());
        embed.addField("Reason:", reason, false);
        embed.setFooter("Kicked by " + DiscordUtils.getUserTagAndId(user), null);
        embed.setTimestamp(now.toInstant());

        DiscordUtils.sendDM(kickUser, embed.build());

        try {
            String auditLogReason = String.format("Kicked by %s - %s", DiscordUtils.getUserTagAndId(user), reason);
            controller.kick(kickMember, auditLogReason).complete();
            DiscordUtils.successReact(bot, message);

            DSLContext database = bot.getDatabase();

            KicklistRecord record = database.insertInto(Tables.KICKLIST,
                                                        Tables.KICKLIST.USERID,
                                                        Tables.KICKLIST.MODERATORUSERID,
                                                        Tables.KICKLIST.GUILDID,
                                                        Tables.KICKLIST.KICKTIME,
                                                        Tables.KICKLIST.REASON)
                                            .values(kickUser.getId(),
                                                    user.getId(),
                                                    guild.getId(),
                                                    now.getTime() / 1000,
                                                    reason)
                                            .returning(Tables.KICKLIST.ID)
                                            .fetchOne();

            DiscordUtils.createModLogEntry(bot, shard, message, kickMember, reason, "kick", record.getId(), null, false);
            DiscordUtils.sendMessage(channel, "Kicked " + DiscordUtils.getUserTagAndId(kickUser));
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Could not kick the specified user. Do I have enough permissions?");
        }

        return false;
    }
}
