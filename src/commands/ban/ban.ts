import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import * as time from 'time-parser';

class Ban implements Command {
    public usage = 'ban @user [reason] | [time] - bans the user with specific args. Both arguments can be omitted.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            bot.failReact(msg);
            msg.channel.send('I don\'t have enough permissions to do that!');
            return;
        }

        await bot.client.fetchUser(msg.mentions.users.first().id);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            bot.failReact(msg);
            msg.channel.send('You can\'t ban yourself, dummy!');
            return false;
        }

        if (!member.bannable) {
            bot.failReact(msg);
            msg.channel.send('I don\'t have enough permissions to do that!');
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
                bot.failReact(msg);
                msg.channel.send(`Invalid time argument \`${timeArg}\`. Try again.`);
                return;
            }
            if (parsedTime.relative < 0) {
                bot.failReact(msg);
                msg.channel.send('Your time argument was set for the past. Try again.' +
                '\nIf you\'re specifying a date, e.g. `30 December`, make sure you pass the year.');
                return;
            }
        } else if (args.length > 0) {
            reason = args;
        }
        if (!reason) {
            reason = 'No reason specified';
        }

        bot.database.getGuildConfiguration(msg.guild).then((config) => {
        let embed = {
            title: `Banned from ${msg.guild.name}`,
            color: parseInt(config.EmbedColor, 16),
            description: `You were banned from ${msg.guild.name}.`,
            fields: [
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Banned until', value: parsedTime ? new Date(parsedTime.absolute).toString() : 'Indefinitely' },
            ],
            footer: { text: `Banned by ${member.user.tag} (${member.id})` },
            timestamp: new Date(),
        };

        member.send({ embed })
            .then(() => {
                bot.successReact(msg);
                member.ban(reason);
            });
        })
            .catch(() => {
                bot.successReact(msg);
                member.ban(reason);
        });

        bot.database.createUserBan(
            member.user,
            msg.author,
            msg.guild,
            reason,
            parsedTime ? Math.round(parsedTime.absolute / 1000) : null);

        this.createModLogEntry(bot, msg, member,
                               reason, parsedTime ? parsedTime.absolute : null);
        return;
    }

    private async createModLogEntry(bot: SafetyJim, msg: Discord.Message,
                                    member: Discord.GuildMember, reason: string, parsedTime: number): Promise<void> {
    let db = await bot.database.getGuildConfiguration(msg.guild);
    let prefix = await bot.database.getGuildPrefix(msg.guild);

    if (!db || !db.ModLogActive) {
        return;
    }

    if (!bot.client.channels.has(db.ModLogChannelID) ||
        bot.client.channels.get(db.ModLogChannelID).type !== 'text') {
        // tslint:disable-next-line:max-line-length
        msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
        return;
    }

    let logChannel = bot.client.channels.get(db.ModLogChannelID) as Discord.TextChannel;

    let embed = {
        color: 0xFF2900, // red
        fields: [
            { name: 'Action:', value: 'Ban' },
            { name: 'User:', value: `${member.user.tag} (${member.id})`, inline: false },
            { name: 'Reason:', value: reason, inline: false },
            { name: 'Responsible Moderator:', value: msg.author.tag, inline: false },
            { name: 'Banned until', value: parsedTime ? new Date(parsedTime).toString() : 'Indefinitely' },
        ],
        timestamp: new Date(),
    };
    logChannel.send({ embed });
    return;
    }
}
export = Ban;
