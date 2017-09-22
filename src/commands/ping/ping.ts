import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Discord from 'discord.js';
import * as Utils from '../../safetyjim/utils';

class Ping implements Command {
    public usage = 'ping - pong';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        await shard.successReact(msg);
        await Utils.sendMessage(msg.channel, { embed: {
            author: {
                name: `Safety Jim [${shard.shardId + 1}, ${jim.config.jim.shard_count}]`,
                icon_url: shard.client.user.avatarURL,
            },
            description: `:ping_pong: Ping: ${shard.client.pings[0].toFixed(0)}ms`,
            color: 0x4286f4,
        }});

        return;
    }
}

export = Ping;
