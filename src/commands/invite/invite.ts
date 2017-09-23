import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';

const botLink = 'https://discordapp.com/oauth2/authorize?client_id=313749262687141888&permissions=268446790&scope=bot';
const inviteLink = 'https://discord.io/safetyjim';

class Invite implements Command {
    public usage = 'ping - pong';
    private embed = {
        author: {
            name: `Safety Jim`,
            icon_url: undefined,
        },
        fields: [
            { name: 'Invite Jim!', value: `[Here](${botLink})`, inline: true },
            { name: 'Join our support server!', value: `[Here](${inviteLink})`, inline: true },
        ],
        color: 0x4286f4,
    };

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        await Utils.successReact(msg);

        if (this.embed.author.icon_url == null) {
            this.embed.author.icon_url = shard.client.user.avatarURL;
        }
        await Utils.sendMessage(msg.channel, { embed: this.embed });
        return;
    }
}

export = Invite;
