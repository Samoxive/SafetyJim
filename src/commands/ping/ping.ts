import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Ping implements Command {
    public usage = 'ping - pong';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        bot.successReact(msg);
        msg.channel.send('', { embed: {
            author: {
                name: `Safety Jim`,
                icon_url: bot.client.user.avatarURL,
            },
            description: `:ping_pong: Ping: ${bot.client.ping.toFixed(0)}`,
            color: 0x4286f4,
        }});

        return;
    }
}

export = Ping;
