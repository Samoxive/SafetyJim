import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';
import * as time from 'time-parser';
import { Bans } from '../../database/models/Bans';
import { Settings } from '../../database/models/Settings';

class Ban implements Command {
    public usage = 'ban @user [reason] | [time] - bans the user with specific args. Both arguments can be omitted.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (!msg.member.hasPermission('BAN_MEMBERS')) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
            return;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'I don\'t have enough permissions to do that!');
            return;
        }

        await shard.client.fetchUser(msg.mentions.users.first().id, true);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'You can\'t ban yourself, dummy!');
            return false;
        }

        if (!member.bannable) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'I don\'t have enough permissions to do that!');
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
                await Utils.failReact(msg);
                await Utils.sendMessage(msg.channel, `Invalid time argument \`${timeArg}\`. Try again.`);
                return;
            }
            if (parsedTime.relative < 0) {
                await Utils.failReact(msg);
                await Utils.sendMessage(msg.channel, 'Your time argument was set for the past. Try again.' +
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
            title: `Banned from ${msg.guild.name}`,
            color: 0x4286f4,
            description: `You were banned from ${msg.guild.name}.`,
            fields: [
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Banned until', value: parsedTime ? new Date(parsedTime.absolute).toString() : 'Indefinitely' },
            ],
            footer: { text: `Banned by ${msg.author.tag} (${msg.author.id})` },
            timestamp: new Date(),
        };

        try {
            await member.send({ embed });
        } catch (e) {
            // tslint:disable-next-line:max-line-length
            await Utils.sendMessage(msg.channel, 'Could not send a private message to specified user, I am probably blocked.');
        } finally {
            try {
                let auditLogReason = `Banned by ${msg.author.tag} (${msg.author.id}) - ${reason}`;
                await member.ban({ days: 0, reason: auditLogReason });
                await Utils.successReact(msg);

                let now = Math.round((new Date()).getTime() / 1000);
                let expires = parsedTime != null;

                let banRecord = await Bans.create<Bans>({
                    userid: member.user.id,
                    moderatoruserid: msg.author.id,
                    guildid: msg.guild.id,
                    bantime: now,
                    expiretime: expires ? Math.round(parsedTime.absolute / 1000) : 0,
                    reason,
                    expires,
                    unbanned: false,
                });

                await Utils.createModLogEntry(shard, msg, member, reason, 'ban',
                                            banRecord.id, parsedTime ? parsedTime.absolute : null);
            } catch (e) {
                await Utils.failReact(msg);
                await Utils.sendMessage(msg.channel, 'Could not ban specified user. Do I have enough permissions?');
            }
        }

        await Utils.deleteCommandMessage(jim, msg);
        return;
    }
}
export = Ban;
