import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Ping implements Command {
    public usage = 'ping - pongs when you ping doofus';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        msg.channel.send('pong');
        return true;
    }
}

export = Ping;
