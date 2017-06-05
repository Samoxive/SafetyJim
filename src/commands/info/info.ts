import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Info implements Command {
    public usage = 'info - displays some information about the bot';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        this.asyncRun(bot, msg, args);
        return;
    }

    private async asyncRun(bot: SafetyJim, msg: Discord.Message, args: string): Promise<void> {
        let config = await bot.database.getGuildConfiguration(msg.guild);
        let uptimeString = this.millisecondsToStr((new Date()).getTime() - bot.bootTime.getTime());
        let embed = new Discord.RichEmbed({
            author: { name: 'Safety Jim - v0.0.1', icon_url: bot.client.user.avatarURL },
            description: `Lifting the :hammer: since ${uptimeString} ago.`,
            fields: [
                { name: 'Guild Count', value:  bot.client.guilds.size.toString(), inline: true },
                { name: 'User Count', value: bot.client.users.size.toString(), inline: true },
                { name: 'Channel Count', value: bot.client.channels.size.toString(), inline: true },
                { name: 'Websocket Ping', value: `${bot.client.ping.toFixed(0)}ms`, inline: true},
                // tslint:disable-next-line:max-line-length
                { name: 'RAM usage', value: `${(process.memoryUsage().rss / (1024 * 1024)).toFixed(0)}MB`, inline: true },
                { name: 'Support', value: '[discord.io/safetyjim](https://discord.io/safetyjim)', inline: true },
            ],
            footer: { text: `Made by Samoxive#8634 and Aetheryx#2222.`},
            color: parseInt(config.EmbedColor, 16),
        });

        msg.channel.send('', {embed});
    }

    private millisecondsToStr(milliseconds: number): string {
        function numberEnding(n: number) {
            return (n > 1) ? 's' : '';
        }

        let temp = Math.floor(milliseconds / 1000);
        let years = Math.floor(temp / 31536000);
        if (years) {
            return years + ' year' + numberEnding(years);
        }
        let days = Math.floor((temp %= 31536000) / 86400);
        if (days) {
            return days + ' day' + numberEnding(days);
        }
        let hours = Math.floor((temp %= 86400) / 3600);
        if (hours) {
            return hours + ' hour' + numberEnding(hours);
        }
        let minutes = Math.floor((temp %= 3600) / 60);
        if (minutes) {
            return minutes + ' minute' + numberEnding(minutes);
        }
        let seconds = temp % 60;
        if (seconds) {
            return seconds + ' second' + numberEnding(seconds);
        }
        return 'less than a second';
    }
}

export = Info;
