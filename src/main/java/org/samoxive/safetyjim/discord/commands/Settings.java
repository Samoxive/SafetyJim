package org.samoxive.safetyjim.discord.commands;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import org.jooq.DSLContext;
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
                                     "silentcommands" };

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
                                        "`SilentCommands <enabled/disabled>` - Default: disabled";

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

        Map<String, String> config = DatabaseUtils.getGuildSettings(database, guild);
        StringJoiner output = new StringJoiner("\n");

        if (config.get("modlogactive").equals("false")) {
            output.add("**Mod Log:** Disabled");
        } else {
            TextChannel modLogChannel = guild.getTextChannelById(config.get("modlogchannelid"));
            output.add("**Mod Log:** Enabled");
            output.add("\t**Mod Log Channel:** " + (modLogChannel == null ? "null" : modLogChannel.getAsMention()));
        }

        if (config.get("welcomemessageactive").equals("false")) {
            output.add("**Welcome Messages:** Disabled");
        } else {
            TextChannel welcomeMessageChannel = guild.getTextChannelById(config.get("welcomemessagechannelid"));
            output.add("**Welcome Messages:** Enabled");
            output.add("\t**Welcome Message Channel:** " + (welcomeMessageChannel == null ? "null" : welcomeMessageChannel.getAsMention()));
        }

        if (config.get("holdingroomactive").equals("false")) {
            output.add("**Holding Room:** Disabled");
        } else {
            String holdingRoomMinutes = config.get("holdingroomminutes");
            String holdingRoomRoleId = config.get("holdingroomroleid");
            Role holdingRoomRole = guild.getRoleById(holdingRoomRoleId);
            output.add("**Holding Room:** Enabled");
            output.add("\t**Holding Room Role:** " + (holdingRoomRole == null ? "null" : holdingRoomRole.getName()));
            output.add("\t**Holding Room Delay:** " + holdingRoomMinutes + " minute(s)");
        }

        if (config.get("invitelinkremover").equals("true")) {
            output.add("**Invite Link Remover:** Enabled");
        } else {
            output.add("**Invite Link Remover:** Disabled");
        }

        if (config.get("silentcommands").equals("true")) {
            output.add("**Silent Commands:** Enabled");
        } else {
            output.add("**Silent Commands:** Disabled");
        }

        return output.toString();
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

        switch (key) {
            case "silentcommands":
            case "invitelinkremover":
            case "welcomemessage":
            case "modlog":
                if (argument.equals("enabled")) {
                    argument = "true";
                } else if (argument.equals("disabled")) {
                    argument = "false";
                } else {
                    return true;
                }

                key = key.equals("welcomemessage") ? "welcomemessageactive" : key;
                key = key.equals("modlog") ? "modlogactive" : key;

                DatabaseUtils.updateGuildSetting(database, guild, key, argument);
                DiscordUtils.successReact(bot, message);
                break;
            case "welcomemessagechannel":
            case "modlogchannel":
                argument = argumentSplit[0];

                if (!DiscordUtils.CHANNEL_MENTION_PATTERN.matcher(argument).matches()) {
                    return true;
                }

                key = (key.equals("modlogchannel")) ? "modlogchannelid" : "welcomemessagechannelid";
                TextChannel argumentChannel = message.getMentionedChannels().get(0);
                DatabaseUtils.updateGuildSetting(database, guild, key, argumentChannel.getId());
                DiscordUtils.successReact(bot, message);
                break;
            case "holdingroomminutes":
                int minutes;

                try {
                    minutes = Integer.parseInt(argumentSplit[0]);
                } catch (NumberFormatException e) {
                    return true;
                }

                DatabaseUtils.updateGuildSetting(database, guild, key, Integer.toString(minutes));
                DiscordUtils.successReact(bot, message);
                break;
            case "prefix":
                DatabaseUtils.updateGuildSetting(database, guild, key, argumentSplit[0]);
                DiscordUtils.successReact(bot, message);
                break;
            case "message":
                DatabaseUtils.updateGuildSetting(database, guild, "welcomemessage", argument);
                DiscordUtils.successReact(bot, message);
                break;
            case "holdingroom":
                if (argument.equals("enabled")) {
                    argument = "true";
                } else if (argument.equals("disabled")) {
                    argument = "false";
                } else {
                    return true;
                }

                String roleId = DatabaseUtils.getGuildSetting(database, guild, "holdingroomroleid");

                if (roleId == null) {
                    DiscordUtils.failMessage(bot, message, "You can't enable holding room before setting a role for it first.");
                    return false;
                }

                DiscordUtils.successReact(bot, message);
                DatabaseUtils.updateGuildSetting(database, guild, "holdingroomactive", argument);
                break;
            case "holdingroomrole":
                List<Role> foundRoles = guild.getRolesByName(argument, true);
                if (foundRoles.size() == 0) {
                    return true;
                }

                Role role = foundRoles.get(0);
                DatabaseUtils.updateGuildSetting(database, guild, "holdingroomroleid", role.getId());
                DiscordUtils.successReact(bot, message);
                break;
            default:
                return true;
        }

        return false;
    }
}
