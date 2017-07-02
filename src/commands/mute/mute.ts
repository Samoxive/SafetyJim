import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import * as time from 'time-parser';

class Mute implements Command {
    public usage = 'mute @user [reason] | [time] - mutes the user with specific args. Both arguments can be omitted.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('MANAGE_ROLES')) {
            await bot.failReact(msg);
            await msg.channel.send('I don\'t have enough permissions to do that!');
            return;
        }

        if (!msg.guild.roles.find('name', 'Muted')) {
            let mutedRole = await msg.guild.createRole({
                name: 'Muted',
                permissions: ['READ_MESSAGES', 'READ_MESSAGE_HISTORY', 'CONNECT'],
            });

            msg.guild.channels.forEach((channel) => {
                channel.overwritePermissions(mutedRole, {
                    SEND_MESSAGES: false,
                    ADD_REACTIONS: false,
                    SPEAK: false,
                });
            });
        }

        await bot.client.fetchUser(msg.mentions.users.first().id);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t mute yourself, dummy!');
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
                await msg.channel.send(`Invalid time argument \`${timeArg}\`. Try again.`);
                return;
            }
            if (parsedTime.relative < 0) {
                await bot.failReact(msg);
                await msg.channel.send('Your time argument was set for the past. Try again.' +
                '\nIf you\'re specifying a date, e.g. `30 December`, make sure you pass the year.');
                return;
            }
        } else if (args.length > 0) {
            reason = args;
        }
        if (!reason) {
            reason = 'No reason specified';
        }

        let config = await bot.database.getGuildConfiguration(msg.guild);
        let embed = {
            title: `Muted in ${msg.guild.name}`,
            color: parseInt(config.EmbedColor, 16),
            description: `You were muted in ${msg.guild.name}.`,
            fields: [
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Muted until', value: parsedTime ? new Date(parsedTime.absolute).toString() : 'Indefinitely' },
            ],
            footer: { text: `Muted by ${msg.author.tag} (${msg.author.id})` },
            timestamp: new Date(),
        };

        try {
            member.send({ embed });
        } finally {
            bot.successReact(msg);
            member.addRole(msg.guild.roles.find('name', 'Muted'));
        }

        await bot.database.createUserMute(
            member.user,
            msg.author,
            msg.guild,
            reason,
            parsedTime ? Math.round(parsedTime.absolute / 1000) : null);

        await this.createModLogEntry(bot, msg, member,
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
        await msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
        return;
    }

    let logChannel = bot.client.channels.get(db.ModLogChannelID) as Discord.TextChannel;

    let embed = {
        color: 0xFFFFFF, // white
        fields: [
            { name: 'Action:', value: 'Mute' },
            { name: 'User:', value: `${member.user.tag} (${member.id})`, inline: false },
            { name: 'Reason:', value: reason, inline: false },
            { name: 'Responsible Moderator:', value: `${msg.author.tag} (${msg.author.id})`, inline: false },
            { name: 'Muted until', value: parsedTime ? new Date(parsedTime).toString() : 'Indefinitely' },
        ],
        timestamp: new Date(),
    };

    await logChannel.send({ embed });
    return;
    }
}
export = Mute;
