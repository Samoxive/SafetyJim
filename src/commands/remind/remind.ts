import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
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

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
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
                await Utils.failReact(jim, msg);
                await Utils.sendMessage(msg.channel, `Invalid time argument \`${splitArgs[1]}\`. Try again.`);
                return;
            }

            if (parsedTime.relative < 0) {
                await Utils.failReact(jim, msg);
                await Utils.sendMessage(msg.channel, 'Your time argument was set for the past. Try again.' +
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
            reminded: false,
        });

        await Utils.successReact(jim, msg);
        return;
    }
}

export = Remind;
