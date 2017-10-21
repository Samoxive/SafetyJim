package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.managers.GuildController;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class DiscordUtils {
    private static final Logger log = LoggerFactory.getLogger(DiscordUtils.class);
    private static final String[] botListIds = {"110373943822540800", // DiscordBots
                                                "264445053596991498", // DiscordBotList
                                                "297462937646530562", // NovoBotList
                                               };
    private static final String SUCCESS_EMOTE_ID = "322698554294534144";
    private static final String FAIL_EMOTE_ID = "322698553980092417";
    private static Emote SUCCESS_EMOTE;
    private static Emote FAIL_EMOTE;

    public static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?([0-9]+)>");
    public static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#!?([0-9]+)>");
    public static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&!?([0-9]+)>");

    public static final Map<String, Color> modLogColors = new HashMap<>();
    static {
        modLogColors.put("ban", new Color(0xFF2900));
        modLogColors.put("kick", new Color(0xFF9900));
        modLogColors.put("warn", new Color(0xFFEB00));
        modLogColors.put("mute", new Color(0xFFFFFF));
        modLogColors.put("softban", new Color(0xFF55DD));
    }

    public static final Map<String, String> modLogActionTexts = new HashMap<>();
    static {
        modLogActionTexts.put("ban", "Ban");
        modLogActionTexts.put("softban", "Softban");
        modLogActionTexts.put("kick", "Kick");
        modLogActionTexts.put("warn", "Warn");
        modLogActionTexts.put("mute", "Mute");
    }

    public static void createModLogEntry(DiscordBot bot, JDA shard, Message message, Member member, String reason, String action, int id, Date expirationDate, boolean expires) {
        Map<String, String> guildSettings = DatabaseUtils.getGuildSettings(bot.getDatabase(), member.getGuild());
        Date now = new Date();

        boolean modLogActive = guildSettings.get("modlogactive").equals("true");
        String prefix = guildSettings.get("prefix");

        if (!modLogActive) {
            return;
        }

        TextChannel modLogChannel = shard.getTextChannelById(guildSettings.get("modlogchannelid"));

        if (modLogChannel == null) {
            sendMessage(message.getChannel(), "Invalid moderator log channel in guild configuration, set a proper one via `" + prefix + " settings` command.");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        User user = member.getUser();
        TextChannel channel = message.getTextChannel();
        embed.setColor(modLogColors.get(action));
        embed.addField("Action ", modLogActionTexts.get(action) + "- #" + id, false);
        embed.addField("User:", getUserTagAndId(user), false);
        embed.addField("Reason:", reason, false);
        embed.addField("Responsible Moderator:", getUserTagAndId(message.getAuthor()), false);
        embed.addField("Channel", getChannelMention(channel), false);
        embed.setTimestamp(now.toInstant());

        if (expires) {
            String dateText = expirationDate == null ? "Indefinitely" : expirationDate.toString();
            String untilText = null;

            switch (action) {
                case "ban":
                    untilText = "Banned until";
                    break;
                case "mute":
                    untilText = "Muted until";
                    break;
                default:
                    break;
            }

            embed.addField(untilText, dateText, false);
        }

        sendMessage(modLogChannel, embed.build());
    }

    public static void deleteCommandMessage(DiscordBot bot, Message message) {
        boolean silentCommandsActive = DatabaseUtils.getGuildSetting(bot.getDatabase(), message.getGuild(), "silentcommands").equals("true");

        if (!silentCommandsActive) {
            return;
        }

        try {
            message.delete().complete();
        } catch (Exception e) {
            //
        }
    }
    public static boolean isBannable(Member toBan, Member banner) {
        Guild guild = toBan.getGuild();
        User toBanUser = toBan.getUser();
        User bannerUser = banner.getUser();

        // Users can't ban themselves
        if (bannerUser.getId().equals(toBanUser.getId())) {
            return false;
        }

        // Owners cannot be banned
        String ownerId = guild.getOwner().getUser().getId();
        if (ownerId.equals(toBanUser.getId())) {
            return false;
        }

        Role highestRoleToBan = getHighestRole(toBan);
        Role highestRoleBanner = getHighestRole(banner);

        // If either of these variables are null, this means they have no roles
        // If the person we are trying to ban has no roles, there are two possibilities
        // Either banner also doesn't have a role, in which case both users are equal
        // and banner doesn't have the power to ban, or banner has a role which will
        // always equal to being above the to be banned user
        if (highestRoleToBan == null || highestRoleBanner == null) {
            return highestRoleBanner != null;
        }

        return highestRoleToBan.getPosition() < highestRoleBanner.getPosition();
    }

    public static boolean isBotFarm(Guild guild) {
        for (String botList: botListIds) {
            if (guild.getId().equals(botList)) {
                return false;
            }
        }

        int botCount = (int)guild.getMembers().stream().filter(member -> member.getUser().isBot()).count();
        return botCount > 20;

    }

    public static void successReact(DiscordBot bot, Message message) {
        if (SUCCESS_EMOTE == null) {
            for (DiscordShard shard: bot.getShards()) {
                Emote emote = shard.getShard().getEmoteById(SUCCESS_EMOTE_ID);
                if (emote != null) {
                    SUCCESS_EMOTE = emote;
                }
            }

            if (SUCCESS_EMOTE == null) {
                log.error("Success emote couldn't be found. Aborting...");
                System.exit(1);
            }
        }

        reactToMessage(message, SUCCESS_EMOTE);
    }

    public static void failReact(DiscordBot bot, Message message) {
        if (FAIL_EMOTE == null) {
            for (DiscordShard shard: bot.getShards()) {
                Emote emote = shard.getShard().getEmoteById(FAIL_EMOTE_ID);
                if (emote != null) {
                    FAIL_EMOTE = emote;
                }
            }

            if (FAIL_EMOTE == null) {
                log.error("Failure emote couldn't be found. Aborting...");
                System.exit(1);
            }
        }

        reactToMessage(message, FAIL_EMOTE);
    }

    public static void reactToMessage(Message message, Emote emote) {
        try {
            message.addReaction(emote).queue();
        } catch (Exception e) {
            //
        }
    }

    public static void sendMessage(MessageChannel channel, String message) {
        try {
            channel.sendMessage(message).queue();
        } catch (Exception e) {
            //
        }
    }

    public static void sendMessage(MessageChannel channel, MessageEmbed embed) {
        try {
            channel.sendMessage(embed).queue();
        } catch (Exception e) {
            //
        }
    }

    public static void sendDM(User user, String message) {
        PrivateChannel channel = user.openPrivateChannel().complete();
        sendMessage(channel, message);
    }

    public static void sendDM(User user, MessageEmbed embed) {
        PrivateChannel channel = user.openPrivateChannel().complete();
        sendMessage(channel, embed);
    }

    public static String getChannelMention(MessageChannel channel) {
        return "<#" + channel.getId() + ">";
    }

    public static String getUserTagAndId(User user) {
        return getTag(user) + " (" + user.getId() + ")";
    }

    public static Role getHighestRole(Member member) {
        List<Role> roles = member.getRoles();

        if (roles.size() == 0) {
            return null;
        }

        return roles.stream().reduce(((prev, next) -> {
            if (prev != null) {
                return next.getPosition() > prev.getPosition() ? next : prev;
            } else {
                return next;
            }
        })).get();
    }

    public static String getUsageString(String prefix, String[] usages) {
        StringJoiner joiner = new StringJoiner("\n");

        Arrays.stream(usages).map((usage) -> usage.split(" - "))
                             .map((splitUsage) -> String.format("`%s %s` - %s", prefix, splitUsage[0], splitUsage[1]))
                             .forEach((usage) -> joiner.add(usage));

        return joiner.toString();
    }

    public static String getTag(User user) {
        return user.getName() + "#" + user.getDiscriminator();
    }

    public static TextChannel getDefaultChannel(Guild guild) {
        List<TextChannel> channels = guild.getTextChannels();
        for (TextChannel channel: channels) {
            if (channel.canTalk()) {
                return channel;
            }
        }

        return channels.get(0);
    }

    public static int getShardIdFromGuildId(long guildId, int shardCount) {
        // (guild_id >> 22) % num_shards == shard_id
        return (int)((guildId >> 22L) % shardCount);
    }

    public static String getShardString(int shardId, int shardCount) {
        return "[" + shardId + " / " + shardCount + "]";
    }
}
