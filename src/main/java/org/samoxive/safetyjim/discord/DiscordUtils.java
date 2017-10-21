package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
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

    public static void sendMessage(TextChannel channel, String message) {
        try {
            channel.sendMessage(message).queue();
        } catch (Exception e) {
            //
        }
    }

    public static void sendMessage(TextChannel channel, MessageEmbed embed) {
        try {
            channel.sendMessage(embed).queue();
        } catch (Exception e) {
            //
        }
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
