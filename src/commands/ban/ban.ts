import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import * as time from 'time-parser';
import { Bans } from '../../database/models/Bans';
import { Settings } from '../../database/models/Settings';

class Ban implements Command {
    public usage = 'ban @user [reason] | [time] - bans the user with specific args. Both arguments can be omitted.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (!msg.member.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            await msg.channel.send('You don\'t have enough permissions to execute this command!');
            return;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            msg.channel.send('I don\'t have enough permissions to do that!');
            return;
        }

        await bot.client.fetchUser(msg.mentions.users.first().id);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t ban yourself, dummy!');
            return false;
        }

        if (!member.bannable) {
            await bot.failReact(msg);
            await msg.channel.send('I don\'t have enough permissions to do that!');
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

        let EmbedColor = (await Settings.find<Settings>({
            where: {
                guildid: msg.guild.id,
                key: 'embedcolor',
            },
        })).value;

        let embed = {
            title: `Banned from ${msg.guild.name}`,
            color: parseInt(EmbedColor, 16),
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
            await msg.channel.send('Could not send a private message to specified user, I am probably blocked.');
        } finally {
            try {
                await member.ban(reason);
                await bot.successReact(msg);
                await this.createModLogEntry(bot, msg, member,
                                             reason, parsedTime ? parsedTime.absolute : null);

                let now = Math.round((new Date()).getTime() / 1000);
                let expires = parsedTime != null;

                await Bans.create<Bans>({
                    userid: member.user.id,
                    moderatoruserid: msg.author.id,
                    guildid: msg.guild.id,
                    bantime: now,
                    expiretime: expires ? Math.round(parsedTime.absolute / 1000) : 0,
                    reason,
                    expires,
                    unbanned: false,
                });
            } catch (e) {
                await bot.failReact(msg);
                await msg.channel.send('Could not ban specified user. Do I have enough permissions?');
            }
        }

        return;
    }

    private async createModLogEntry(bot: SafetyJim, msg: Discord.Message,
                                    member: Discord.GuildMember, reason: string, parsedTime: number): Promise<void> {
        let ModLogActive = (await Settings.find<Settings>({
            where: {
                guildid: msg.guild.id,
                key: 'modlogactive',
            },
        })).value;

        let prefix = (await Settings.find<Settings>({
            where: {
                guildid: msg.guild.id,
                key: 'prefix',
            },
        })).value;

        if (!ModLogActive || ModLogActive === 'false') {
            return;
        }

        let ModLogChannelID = (await Settings.find<Settings>({
            where: {
                guildid: msg.guild.id,
                key: 'modlogchannelid',
            },
        })).value;

        if (!bot.client.channels.has(ModLogChannelID) ||
            bot.client.channels.get(ModLogChannelID).type !== 'text') {
            // tslint:disable-next-line:max-line-length
            msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
            return;
        }

        let logChannel = bot.client.channels.get(ModLogChannelID) as Discord.TextChannel;

        let embed = {
            color: 0xFF2900, // red
            fields: [
                { name: 'Action:', value: 'Ban' },
                { name: 'User:', value: `${member.user.tag} (${member.id})`, inline: false },
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Responsible Moderator:', value: `${msg.author.tag} (${msg.author.id})`, inline: false },
                { name: 'Banned until', value: parsedTime ? new Date(parsedTime).toString() : 'Indefinitely' },
            ],
            timestamp: new Date(),
        };

        await logChannel.send({ embed });
        return;
    }
}
export = Ban;
