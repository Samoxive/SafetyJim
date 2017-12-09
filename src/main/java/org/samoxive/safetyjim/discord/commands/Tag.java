package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.TaglistRecord;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.awt.*;
import java.util.Scanner;
import java.util.StringJoiner;

public class Tag extends Command {
    private String[] usages = { "tag list - Shows all tags and responses to user",
                                "tag <name> - Responds with reponse of the given tag",
                                "tag add <name> <response> - Adds a tag with the given name and response",
                                "tag edit <name> <response> - Changes response of tag with given name",
                                "tag remove <name> - Deletes tag with the given name" };
    private String[] subcommands = { "list", "add", "edit", "remove" };

    private boolean isSubcommand(String s) {
        for (String subcommand: subcommands) {
            if (s.equals(subcommand)) {
                return true;
            }
        }

        return false;
    }

    private void displayTags(DiscordBot bot, GuildMessageReceivedEvent event) {
        DSLContext database = bot.getDatabase();
        JDA shard = event.getJDA();
        Guild guild = event.getGuild();
        TextChannel channel = event.getChannel();
        Message message = event.getMessage();

        Result<TaglistRecord> records = database.selectFrom(Tables.TAGLIST)
                                                .where(Tables.TAGLIST.GUILDID.eq(guild.getId()))
                                                .fetch();

        if (records.isEmpty()) {
            DiscordUtils.successReact(bot, message);
            DiscordUtils.sendMessage(channel, "No tags have been added yet!");
            return;
        }

        StringJoiner tagString = new StringJoiner("\n");

        for (TaglistRecord record: records) {
            tagString.add("\u2022 `" + record.getName() + "`");
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor("Safety Jim", null, shard.getSelfUser().getAvatarUrl());
        embed.addField("List of tags", tagString.toString(), false);
        embed.setColor(new Color(0x4286F4));

        DiscordUtils.successReact(bot, message);
        DiscordUtils.sendMessage(channel, embed.build());
    }

    private void addTag(DiscordBot bot, GuildMessageReceivedEvent event, Scanner messageIterator) {
        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        Member member = event.getMember();

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to use this command!");
            return;
        }

        if (!messageIterator.hasNext()) {
            DiscordUtils.failMessage(bot, message, "Please provide a tag name and a response to create a new tag!");
            return;
        }

        String tagName = messageIterator.next();

        if (isSubcommand(tagName)) {
            DiscordUtils.failMessage(bot, message, "You can't create a tag with the same name as a subcommand!");
            return;
        }

        String response = TextUtils.seekScannerToEnd(messageIterator);

        if (response.equals("")) {
            DiscordUtils.failMessage(bot, message, "Empty responses aren't allowed!");
            return;
        }

        TaglistRecord record = database.newRecord(Tables.TAGLIST);

        record.setGuildid(guild.getId());
        record.setName(tagName);
        record.setResponse(response);

        try {
            record.store();
            DiscordUtils.successReact(bot, message);
        } catch (Exception e) {
            DiscordUtils.failMessage(bot, message, "Tag `" + tagName + "` already exists!");
        }
    }

    private void editTag(DiscordBot bot, GuildMessageReceivedEvent event, Scanner messageIterator) {
        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        Member member = event.getMember();

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to use this command!");
            return;
        }

        if (!messageIterator.hasNext()) {
            DiscordUtils.failMessage(bot, message, "Please provide a tag name and a response to edit tags!");
            return;
        }

        String tagName = messageIterator.next();
        String response = TextUtils.seekScannerToEnd(messageIterator);

        if (response.equals("")) {
            DiscordUtils.failMessage(bot, message, "Empty responses aren't allowed!");
            return;
        }

        TaglistRecord record = database.selectFrom(Tables.TAGLIST)
                                       .where(Tables.TAGLIST.GUILDID.eq(guild.getId()))
                                       .and(Tables.TAGLIST.NAME.eq(tagName))
                                       .fetchOne();

        if (record == null) {
            DiscordUtils.failMessage(bot, message, "Tag `" + tagName + "` does not exist!");
            return;
        }

        record.setResponse(response);
        record.update();

        DiscordUtils.successReact(bot, message);
    }

    private void deleteTag(DiscordBot bot, GuildMessageReceivedEvent event, Scanner messageIterator) {
        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        Member member = event.getMember();

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to use this command!");
            return;
        }

        if (!messageIterator.hasNext()) {
            DiscordUtils.failMessage(bot, message, "Please provide a tag name and a response to delete tags!");
            return;
        }

        String tagName = messageIterator.next();

        TaglistRecord record = database.selectFrom(Tables.TAGLIST)
                                       .where(Tables.TAGLIST.GUILDID.eq(guild.getId()))
                                       .and(Tables.TAGLIST.NAME.eq(tagName))
                                       .fetchOne();

        if (record == null) {
            DiscordUtils.failMessage(bot, message, "Tag `" + tagName + "` does not exist!");
            return;
        }

        record.delete();
        DiscordUtils.successReact(bot, message);
    }

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Scanner messageIterator = new Scanner(args);
        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        TextChannel channel = event.getChannel();

        if (!messageIterator.hasNext()) {
            return true;
        }

        String commandOrTag = messageIterator.next();

        switch (commandOrTag) {
            case "list":
                displayTags(bot, event);
                break;
            case "add":
                addTag(bot, event, messageIterator);
                break;
            case "edit":
                editTag(bot, event, messageIterator);
                break;
            case "remove":
                deleteTag(bot, event, messageIterator);
                break;
            default:
                TaglistRecord record = database.selectFrom(Tables.TAGLIST)
                                               .where(Tables.TAGLIST.GUILDID.eq(guild.getId()))
                                               .and(Tables.TAGLIST.NAME.eq(commandOrTag))
                                               .fetchAny();

                if (record == null) {
                    DiscordUtils.failMessage(bot, message, "Could not find a tag with that name!");
                    return false;
                }

                DiscordUtils.successReact(bot, message);
                DiscordUtils.sendMessage(channel, record.getResponse());
        }


        return false;
    }
}

