import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import * as time from 'time-parser';

class Ban implements Command {
    public usage = 'ban @user [reason] | [time] - Bans the user with specific args. Both arguments can be omitted.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');
        if (!msg.member.hasPermission('BAN_MEMBERS')) {
            msg.channel.send('You don\'t have permission to use this command.');
            return false;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        let member = msg.guild.member(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            msg.channel.send('You can\'t ban yourself, dummy!');
            return false;
        }

        if (!member.bannable) {
            msg.channel.send('My role isn\'t high enough to ban this person.');
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
                msg.channel.send(`Invalid time argument \`${timeArg}\`. Try again.`);
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
                { name: 'Responsible Moderator:', value: msg.author.tag, inline: false },
                { name: 'Banned until', value: parsedTime ? new Date(parsedTime.absolute).toString() : 'Indefinitely' },
            ],
            footer: { text: `Banned by ${msg.author.tag}` },
            timestamp: new Date(),
        };

        member.send({ embed })
            .then(() => {
                member.ban(reason);
                msg.react('☑');
            });
        });

        bot.database.createUserBan(
            member.user,
            msg.author,
            msg.guild,
            reason,
            parsedTime ? parsedTime.absolute : null);

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
            { name: 'User:', value: member.user.tag, inline: false },
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
