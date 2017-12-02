package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.tables.records.SettingsRecord;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.Command;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.discord.DiscordUtils;
import org.samoxive.safetyjim.discord.TextUtils;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringJoiner;

public class Settings extends Command {
    private String[] usages = { "settings display - shows current state of settings",
                                "settings list - lists the keys you can use to customize the bot",
                                "settings reset - resets every setting to their default value",
                                "settings set <key> <value> - changes given key\'s value" };

    private String[] settingKeys = { "modlog",
                                     "modlogchannel",
                                     "holdingroomrole",
                                     "holdingroom",
                                     "holdingroomminutes",
                                     "prefix",
                                     "welcomemessage",
                                     "message",
                                     "welcomemessagechannel",
                                     "invitelinkremover",
                                     "silentcommands",
                                     "nospaceprefix" };

    private String settingsListString = "`HoldingRoom <enabled/disabled>` - Default: disabled\n" +
                                        "`HoldingRoomMinutes <number>` - Default: 3\n" +
                                        "`HoldingRoomRole <text>` - Default: None\n" +
                                        "`ModLog <enabled/disabled>` - Default: disabled\n" +
                                        "`ModLogChannel <#channel>` - Default: %s\n" +
                                        "`Prefix <text>` - Default: -mod\n" +
                                        "`WelcomeMessage <enabled/disabled>` - Default: disabled\n" +
                                        "`WelcomeMessageChannel <#channel>` - Default: %s\n" +
                                        "`Message <text>` - Default: " + DatabaseUtils.DEFAULT_WELCOME_MESSAGE + "\n" +
                                        "`InviteLinkRemover <enabled/disabled>` - Default: disabled\n" +
                                        "`SilentCommands <enabled/disabled>` - Default: disabled\n" +
                                        "`NoSpacePrefix <enabled/disabled>` - Default: disabled";

    private void handleSettingsDisplay(DiscordBot bot, GuildMessageReceivedEvent event) {
        JDA shard = event.getJDA();
        TextChannel channel = event.getChannel();
        Message message = event.getMessage();
        SelfUser selfUser = shard.getSelfUser();
        String output = getSettingsString(bot, event);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor("Safety Jim", null, selfUser.getAvatarUrl());
        embed.addField("Guild Settings", output, false);
        embed.setColor(new Color(0x4286F4));

        DiscordUtils.successReact(bot, message);
        DiscordUtils.sendMessage(channel, embed.build());
    }

    private String getSettingsString(DiscordBot bot, GuildMessageReceivedEvent event) {
        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();

        SettingsRecord config = DatabaseUtils.getGuildSettings(database, guild);
        StringJoiner output = new StringJoiner("\n");

        if (!config.getModlog()) {
            output.add("**Mod Log:** Disabled");
        } else {
            TextChannel modLogChannel = guild.getTextChannelById(config.getModlogchannelid());
            output.add("**Mod Log:** Enabled");
            output.add("\t**Mod Log Channel:** " + (modLogChannel == null ? "null" : modLogChannel.getAsMention()));
        }

        if (!config.getWelcomemessage()) {
            output.add("**Welcome Messages:** Disabled");
        } else {
            TextChannel welcomeMessageChannel = guild.getTextChannelById(config.getWelcomemessagechannelid());
            output.add("**Welcome Messages:** Enabled");
            output.add("\t**Welcome Message Channel:** " + (welcomeMessageChannel == null ? "null" : welcomeMessageChannel.getAsMention()));
        }

        if (!config.getHoldingroom()) {
            output.add("**Holding Room:** Disabled");
        } else {
            int holdingRoomMinutes = config.getHoldingroomminutes();
            String holdingRoomRoleId = config.getHoldingroomroleid();
            Role holdingRoomRole = guild.getRoleById(holdingRoomRoleId);
            output.add("**Holding Room:** Enabled");
            output.add("\t**Holding Room Role:** " + (holdingRoomRole == null ? "null" : holdingRoomRole.getName()));
            output.add("\t**Holding Room Delay:** " + holdingRoomMinutes + " minute(s)");
        }

        if (config.getInvitelinkremover()) {
            output.add("**Invite Link Remover:** Enabled");
        } else {
            output.add("**Invite Link Remover:** Disabled");
        }

        if (config.getSilentcommands()) {
            output.add("**Silent Commands:** Enabled");
        } else {
            output.add("**Silent Commands:** Disabled");
        }

        if (config.getNospaceprefix()) {
            output.add("**No Space Prefix:** Enabled");
        } else {
            output.add("**No Space Prefix:** Disabled");
        }
        return output.toString();
    }

    private boolean isEnabledInput(String input) throws BadInputException {
        if (input.equals("enabled")) {
            return true;
        } else if (input.equals("disabled")) {
            return false;
        } else {
            throw new BadInputException();
        }
    }

    @Override
    public String[] getUsages() {
        return usages;
    }

    @Override
    public boolean run(DiscordBot bot, GuildMessageReceivedEvent event, String args) {
        Scanner messageIterator = new Scanner(args);

        JDA shard = event.getJDA();
        DSLContext database = bot.getDatabase();

        Member member = event.getMember();
        Message message = event.getMessage();
        TextChannel channel = event.getChannel();
        Guild guild = event.getGuild();
        SelfUser selfUser = shard.getSelfUser();

        if (!messageIterator.hasNext()) {
            return true;
        }

        String subCommand = messageIterator.next();

        if (subCommand.equals("list")) {
            String defaultChannelMention = DiscordUtils.getDefaultChannel(guild).getAsMention();
            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Safety Jim", null, selfUser.getAvatarUrl());
            embed.addField("List of settings", String.format(settingsListString, defaultChannelMention, defaultChannelMention), false);
            embed.setColor(new Color(0x4286F4));
            DiscordUtils.successReact(bot, message);
            DiscordUtils.sendMessage(channel, embed.build());
            return false;
        }

        if (subCommand.equals("display")) {
            handleSettingsDisplay(bot, event);
            return false;
        }

        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to modify guild settings! Required permission: Administrator");
            return false;
        }

        if (subCommand.equals("reset")) {
            DatabaseUtils.deleteGuildSettings(database, guild);
            DatabaseUtils.createGuildSettings(bot, database, guild);
            DiscordUtils.successReact(bot, message);
            return false;
        }

        if (!subCommand.equals("set")) {
            return true;
        }

        if (!messageIterator.hasNext()) {
            return true;
        }

        String key = messageIterator.next().toLowerCase();
        String argument = TextUtils.seekScannerToEnd(messageIterator);
        String[] argumentSplit = argument.split(" ");

        if (argument.equals("")) {
            return true;
        }

        boolean isKeyOkay = false;
        for (String possibleKey: settingKeys) {
            if (possibleKey.equals(key)) {
                isKeyOkay = true;
            }
        }

        if (!isKeyOkay) {
            DiscordUtils.failMessage(bot, message, "Please enter a valid setting key!");
            return false;
        }

        SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);
        TextChannel argumentChannel;

        try {
            switch (key) {
                case "silentcommands":
                    guildSettings.setSilentcommands(isEnabledInput(argument));
                    break;
                case "invitelinkremover":
                    guildSettings.setInvitelinkremover(isEnabledInput(argument));
                    break;
                case "welcomemessage":
                    guildSettings.setWelcomemessage(isEnabledInput(argument));
                    break;
                case "modlog":
                    guildSettings.setModlog(isEnabledInput(argument));
                    break;
                case "welcomemessagechannel":
                    argument = argumentSplit[0];

                    if (!DiscordUtils.CHANNEL_MENTION_PATTERN.matcher(argument).matches()) {
                        return true;
                    }

                    argumentChannel = message.getMentionedChannels().get(0);
                    guildSettings.setWelcomemessagechannelid(argumentChannel.getId());
                    break;
                case "modlogchannel":
                    argument = argumentSplit[0];

                    if (!DiscordUtils.CHANNEL_MENTION_PATTERN.matcher(argument).matches()) {
                        return true;
                    }

                    argumentChannel = message.getMentionedChannels().get(0);
                    guildSettings.setModlogchannelid(argumentChannel.getId());
                    break;
                case "holdingroomminutes":
                    int minutes;

                    try {
                        minutes = Integer.parseInt(argumentSplit[0]);
                    } catch (NumberFormatException e) {
                        return true;
                    }

                    guildSettings.setHoldingroomminutes(minutes);
                    break;
                case "prefix":
                    guildSettings.setPrefix(argumentSplit[0]);
                    break;
                case "message":
                    guildSettings.setMessage(argument);
                    break;
                case "holdingroom":
                    boolean holdingRoomEnabled = isEnabledInput(argument);
                    String roleId = guildSettings.getHoldingroomroleid();

                    if (roleId == null) {
                        DiscordUtils.failMessage(bot, message, "You can't enable holding room before setting a role for it first.");
                        return false;
                    }

                    guildSettings.setHoldingroom(holdingRoomEnabled);
                    break;
                case "holdingroomrole":
                    List<Role> foundRoles = guild.getRolesByName(argument, true);
                    if (foundRoles.size() == 0) {
                        return true;
                    }

                    Role role = foundRoles.get(0);
                    guildSettings.setHoldingroomroleid(role.getId());
                    break;
                case "nospaceprefix":
                    guildSettings.setNospaceprefix(isEnabledInput(argument));
                    break;
                default:
                    return true;
            }
        } catch (BadInputException e) {
            return true;
        }

        guildSettings.update();
        DiscordUtils.successReact(bot, message);
        return false;
    }

    private static class BadInputException extends Exception {}
}
