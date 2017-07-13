import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Info implements Command {
    public usage = 'info - displays some information about the bot';

    private MAGNITUDES = [
        [1000 * 60 * 60 * 24 * 30, 'months'],
        [1000 * 60 * 60 * 24, 'days'],
        [1000 * 60 * 60, 'hours'],
        [1000 * 60, 'minutes'],
        [1000, 'seconds'],
    ];

    // tslint:disable-next-line:max-line-length
    private inviteLink = 'https://discordapp.com/oauth2/authorize?client_id=313749262687141888&permissions=268446790&scope=bot';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let EmbedColor = await bot.database.getSetting(msg.guild, 'EmbedColor');
        let uptimeString = this.timeElapsed(Date.now(), bot.bootTime.getTime());
        let embed = {
            author: { name: `Safety Jim - v${bot.config.version}`,
                      icon_url: bot.client.user.avatarURL,
                      url: 'https://discordbots.org/bot/313749262687141888' },
            description: `Lifting the :hammer: since ${uptimeString} ago.`,
            fields: [
                { name: 'Server Count', value:  bot.client.guilds.size, inline: true },
                { name: 'User Count', value: bot.client.users.size, inline: true },
                { name: 'Channel Count', value: bot.client.channels.size, inline: true },
                { name: 'Websocket Ping', value: `${bot.client.pings[0].toFixed(0)}ms`, inline: true},
                // tslint:disable-next-line:max-line-length
                { name: 'RAM usage', value: `${(process.memoryUsage().rss / (1024 * 1024)).toFixed(0)}MB`, inline: true },
                { name: 'Links', value: `[Support](https://discord.io/safetyjim) | [Github](https://github.com/samoxive/safetyjim) | [Invite](${this.inviteLink})`, inline: true },
            ],
            footer: { text: `Made by Safety Jim team.`},
            color: parseInt(EmbedColor, 16),
        };

        await bot.successReact(msg);
        await msg.channel.send({ embed });
        return;
    }

    private timeElapsed(before: number, after: number) {
        let diff = Math.abs(after - before);
        return this.MAGNITUDES.reduce((out, m: [number, string]) => {
          const current = Math.floor(diff / m[0]);
          diff %= m[0];
          if (out.length || current) {
            out.push(`${current} ${m[1]}`);
          }
          return out;
        }, []).join(' and ');
    }
}

export = Info;
