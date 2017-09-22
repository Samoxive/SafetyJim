import * as Discord from 'discord.js';

export function findShardIdFromGuildId(guildId: string, shardCount: number): number {
    // (guild_id >> 22) % num_shards == shard_id

    let id = parseInt(guildId);
    return (id >> 22) % shardCount;
}

export function getDefaultChannel(guild: Discord.Guild): Discord.TextChannel {
    for (let [id, channel] of guild.channels) {
        if (channel.permissionsFor(guild.me).has('SEND_MESSAGES') && channel.type === 'text') {
            return channel as Discord.TextChannel;
        }
    }

    return guild.channels.first() as Discord.TextChannel;
}

export async function sendMessage(channel: Discord.Channel, message: string | Discord.MessageOptions): Promise<void> {
    let textChannel = channel as Discord.TextChannel;
    try {
        await textChannel.send(message);
    } catch (e) {
        this.log.warn(`Could not send a message in guild "${textChannel.guild.name}"`);
    }
}
