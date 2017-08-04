import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';
import { Bans } from '../../database/models/Bans';

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
        let EmbedColor = (await Settings.find<Settings>({
            where: {
                guildid: msg.guild.id,
                key: 'embedcolor',
            },
        })).value;
        let lastBan = await Bans.find<Bans>({
            where: {
                guildid: msg.guild.id,
            },
            order: [
                ['bantime', 'DESC'],
            ],
        });
        let daysSince = '∞';

        if (lastBan != null) {
            daysSince = this.daysSinceBan(lastBan.bantime).toString();
        }
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
            footer: { text: `Made by Safety Jim team. | Days since last incident: ${daysSince}`},
            color: parseInt(EmbedColor, 16),
        };

        await bot.successReact(msg);
        await msg.channel.send({ embed });
        return;
    }

    private daysSinceBan(timestamp: number): number {
        let now = Date.now() / 1000 | 0;
        let delta = now - timestamp;

        return (delta / (60 * 60 * 24)) | 0;
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
