package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.managers.GuildController;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.BanlistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;
import java.util.List;
import java.util.Scanner;

public class Unban extends Command {
    private String[] usages = { "unban <tag> - unbans user with specified user tag (example#1998)" };

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Scanner messageIterator = new Scanner(args);

        Member member = event.getMember();
        Message message = event.getMessage();
        Guild guild = event.getGuild();
        Member selfMember = guild.getSelfMember();
        GuildController controller = guild.getController();


        if (!member.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Ban Members");
            return false;
        }

        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            DiscordUtils.failMessage(bot, message, "I do not have enough permissions to do that!");
            return false;
        }

        String unbanArgument = TextUtils.seekScannerToEnd(messageIterator);

        if (unbanArgument.equals("")) {
            return true;
        }

        List<Guild.Ban> bans = guild.getBanList().complete();

        User targetUser = bans.stream()
                              .filter((ban) -> {
                                  String tag = DiscordUtils.getTag(ban.getUser());
                                  return tag.equals(unbanArgument);
                              })
                              .map((ban) -> ban.getUser())
                              .findAny()
                              .orElse(null);

        if (targetUser == null) {
            DiscordUtils.failMessage(bot, message, "Could not find a banned user called `" + unbanArgument + "`!");
            return false;
        }

        controller.unban(targetUser).complete();
        DSLContext database = bot.getDatabase();

        Result<BanlistRecord> records = database.selectFrom(Tables.BANLIST)
                                                .where(Tables.BANLIST.GUILDID.eq(guild.getId()))
                                                .and(Tables.BANLIST.USERID.eq(targetUser.getId()))
                                                .fetch();

        for (BanlistRecord record: records) {
            record.setUnbanned(true);
            record.update();
        }

        DiscordUtils.successReact(bot, message);

        return false;
    }
}
