package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.RolelistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class RoleCommand extends Command {
    private String[] usages = {
            "role add <roleName> - adds a new self-assignable role",
            "role remove <roleName> - removes a self-assignable role",
    };

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Scanner messageIterator = new Scanner(args);
        DSLContext database = bot.getDatabase();

        Member member = event.getMember();
        Message message = event.getMessage();
        Guild guild = event.getGuild();

        if (!messageIterator.hasNext()) {
            return true;
        }

        String subcommand = messageIterator.next();
        switch (subcommand) {
            case "add":
            case "remove":
                break;
            default:
                return true;
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Administrator");
            return false;
        }

        String roleName = TextUtils.seekScannerToEnd(messageIterator).toLowerCase();

        if (roleName.equals("")) {
            return true;
        }

        List<Role> matchingRoles = guild.getRoles()
                                       .stream()
                                       .filter((role) -> role.getName().toLowerCase().equals(roleName))
                                       .collect(Collectors.toList());

        if (matchingRoles.size() == 0) {
            DiscordUtils.failMessage(bot, message, "Could not find a role with specified name!");
            return false;
        }

        Role matchedRole = matchingRoles.get(0);

        if (subcommand.equals("add")) {
            RolelistRecord record = database.selectFrom(Tables.ROLELIST)
                    .where(Tables.ROLELIST.GUILDID.eq(guild.getId()))
                    .and(Tables.ROLELIST.ROLEID.eq(matchedRole.getId()))
                    .fetchAny();

            if (record == null) {
                record = database.newRecord(Tables.ROLELIST);
                record.setGuildid(guild.getId());
                record.setRoleid(matchedRole.getId());
                record.store();
                DiscordUtils.successReact(bot, message);
            } else {
                DiscordUtils.failMessage(bot, message, "Specified role is already in self-assignable roles list!");
                return false;
            }
        } else {
            RolelistRecord record = database.selectFrom(Tables.ROLELIST)
                    .where(Tables.ROLELIST.GUILDID.eq(guild.getId()))
                    .and(Tables.ROLELIST.ROLEID.eq(matchedRole.getId()))
                    .fetchAny();

            if (record == null) {
                DiscordUtils.failMessage(bot, message, "Specified role is not in self-assignable roles list!");
                return false;
            } else {
                record.delete();
                DiscordUtils.successReact(bot, message);
            }
        }

        return false;
    }
}
