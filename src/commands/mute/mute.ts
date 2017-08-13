import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import * as time from 'time-parser';
import { Settings } from '../../database/models/Settings';
import { Mutes } from '../../database/models/Mutes';

class Mute implements Command {
    public usage = 'mute @user [reason] | [time] - mutes the user with specific args. Both arguments can be omitted.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (!msg.member.hasPermission('MANAGE_ROLES')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
            return;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('MANAGE_ROLES')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'I don\'t have enough permissions to do that!');
            return;
        }

        if (!msg.guild.roles.find('name', 'Muted')) {
            let mutedRole;

            try {
                mutedRole = await msg.guild.createRole({
                    name: 'Muted',
                    permissions: ['READ_MESSAGES', 'READ_MESSAGE_HISTORY', 'CONNECT'],
                });
            } catch (e) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, 'Could not create a Muted role!');
                return;
            }

            for (let [id, channel] of msg.guild.channels) {
                try {
                    await channel.overwritePermissions(mutedRole, {
                        SEND_MESSAGES: false,
                        ADD_REACTIONS: false,
                        SPEAK: false,
                    });
                } catch (e) {
                    await bot.failReact(msg);
                    await bot.sendMessage(msg.channel, 'Could not setup the Muted role!');
                }
            }
        }

        await bot.client.fetchUser(msg.mentions.users.first().id, true);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'You can\'t mute yourself, dummy!');
            return;
        }

        args = args.split(' ').slice(1).join(' ');

        let reason;
        let timeArg;
        let parsedTime;

        if (args.includes('|')) {
            if (args.split('|')[0].trim().length > 0) {
                reason = args.split('|')[0].trim();
            }
            timeArg = args.split('|')[1].trim();
            if (timeArg.startsWith('a ') || timeArg.startsWith('an ')) {
                timeArg = timeArg.replace(/a /g, 'one ').replace(/an /g, 'one ');
            }
            parsedTime = time(timeArg);
            if (!parsedTime.relative) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, `Invalid time argument \`${timeArg}\`. Try again.`);
                return;
            }
            if (parsedTime.relative < 0) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, 'Your time argument was set for the past. Try again.' +
                '\nIf you\'re specifying a date, e.g. `30 December`, make sure you pass the year.');
                return;
            }
        } else if (args.length > 0) {
            reason = args;
        }
        if (!reason) {
            reason = 'No reason specified';
        }

        let embed = {
            title: `Muted in ${msg.guild.name}`,
            color: 0x4286f4,
            description: `You were muted in ${msg.guild.name}.`,
            fields: [
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Muted until', value: parsedTime ? new Date(parsedTime.absolute).toString() : 'Indefinitely' },
            ],
            footer: { text: `Muted by ${msg.author.tag} (${msg.author.id})` },
            timestamp: new Date(),
        };

        try {
            await member.send({ embed });
        } catch (e) {
            // tslint:disable-next-line:max-line-length
            await bot.sendMessage(msg.channel, 'Could not send private message to specified user, I am probably blocked.');
        } finally {
            try {
                await member.addRole(msg.guild.roles.find('name', 'Muted'));
            } catch (e) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, 'I do not have permissions to do that!');
                return;
            }
            await bot.successReact(msg);
        }

        let now = Math.round((new Date()).getTime() / 1000);
        let expires = parsedTime != null;
        let muteRecord = await Mutes.create<Mutes>({
            userid: member.id,
            moderatoruserid: msg.author.id,
            guildid: msg.guild.id,
            mutetime: now,
            expiretime: expires ? Math.round(parsedTime.absolute / 1000) : 0,
            reason,
            expires,
            unmuted: false,
        });

        await bot.createModLogEntry(msg, member, reason, 'mute',
                                    muteRecord.id, parsedTime ? parsedTime.absolute : null);

        await bot.deleteCommandMessage(msg);
        return;
    }
}
export = Mute;
