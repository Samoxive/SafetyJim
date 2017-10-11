package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;

import java.util.List;

public class DiscordUtils {
    private static final String[] botListIds = {"110373943822540800", // DiscordBots
                                                "264445053596991498", // DiscordBotList
                                                "297462937646530562", // NovoBotList
                                               };
    public static boolean isBotFarm(Guild guild) {
        for (String botList: botListIds) {
            if (guild.getId().equals(botList)) {
                return false;
            }
        }

        int botCount = (int)guild.getMembers().stream().filter(member -> member.getUser().isBot()).count();
        return botCount > 20;

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
