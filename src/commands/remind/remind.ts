import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import * as time from 'time-parser';
import { Reminders } from '../../database/models/Reminders';

class Remind implements Command {
    public usage = [
        'remind message - sets a timer to remind you a message in a day',
        'remind message | time - sets a timer to remind you a message in specified time period',
    ];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        if (!args) {
            return true;
        }

        let splitArgs = args.split('|').map((arg) => arg.trim());

        let remindtime: number;
        let now = Math.round((new Date()).getTime() / 1000);

        if (!splitArgs[1]) {
            remindtime = now + 60 * 60 * 24;
        } else {
            let parsedTime = time(splitArgs[1]);

            if (!parsedTime.relative) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, `Invalid time argument \`${splitArgs[1]}\`. Try again.`);
                return;
            }

            if (parsedTime.relative < 0) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, 'Your time argument was set for the past. Try again.' +
                '\nIf you\'re specifying a date, e.g. `30 December`, make sure you pass the year.');
                return;
            }

            remindtime = Math.round(parsedTime.absolute / 1000);
        }

        await Reminders.create<Reminders>({
            userid: msg.author.id,
            channelid: msg.channel.id,
            guildid: msg.guild.id,
            createtime: now,
            remindtime,
            message: splitArgs[0],
        });

        await bot.successReact(msg);
        return;
    }
}

export = Remind;
