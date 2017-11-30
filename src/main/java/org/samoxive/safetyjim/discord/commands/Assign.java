package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.managers.GuildController;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.RolelistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Assign extends Command {
    private String[] usages = { "assign <roleName> - self assigns specified role" };

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

        String roleName = TextUtils.seekScannerToEnd(messageIterator)
                                   .toLowerCase();

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
        Result<RolelistRecord> assignableRoles = database.selectFrom(Tables.ROLELIST)
                                                         .where(Tables.ROLELIST.GUILDID.eq(guild.getId()))
                                                         .and(Tables.ROLELIST.ROLEID.eq(matchedRole.getId()))
                                                         .fetch();

        boolean roleExists = false;
        for (RolelistRecord record: assignableRoles) {
            if (record.getRoleid().equals(matchedRole.getId())) {
                roleExists = true;
            }
        }

        if (!roleExists) {
            DiscordUtils.failMessage(bot, message, "This role is not self-assignable!");
            return false;
        }

        GuildController controller = guild.getController();

        try {
            controller.addSingleRoleToMember(member, matchedRole).complete();
            DiscordUtils.successReact(bot, message);
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Could not assign specified role. Do I have enough permissions?");
        }

        return false;
    }
}
