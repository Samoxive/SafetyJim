import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Softban implements Command {
    public usage = 'softban @user [reason] | [messages to delete (days)] - Softbans the user with the specified args.';

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

        await bot.client.fetchUser(msg.mentions.users.first().id, true);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t softban yourself, dummy!');
            return false;
        }

        if (!member.bannable || !msg.guild.me.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            await msg.channel.send('I don\'t have enough permissions to do that!');
            return;
        }

        args = args.split(' ').slice(1).join(' ');

        let reason;
        let daysArgument;

        if (args.includes('|')) {
            if (args.split('|')[0].trim().length > 0) {
                reason = args.split('|')[0].trim();
            }
            daysArgument = args.split('|')[1].trim();
            if (!parseInt(daysArgument)) {
                bot.failReact(msg);
                return true;
            }
            daysArgument = parseInt(daysArgument);

            if (daysArgument < 1 || daysArgument > 7) {
                bot.failReact(msg);
                msg.channel.send('The amount of days must be between 1 and 7.');
                return;
            }
        } else if (args.length > 0) {
            reason = args;
            daysArgument = 1;
        }
        if (!reason) {
            reason = 'No reason specified';
        }

        let embed = {
            title: `Softbanned from ${msg.guild.name}`,
            color: 0x4286f4,
            description: `You were softbanned from ${msg.guild.name}.`,
            fields: [
                { name: 'Reason:', value: reason, inline: false },
            ],
            footer: { text: `Softbanned by ${msg.author.tag} (${msg.author.id})` },
            timestamp: new Date(),
        };

        try {
            await member.send({ embed });
        } catch (e) {
            await msg.channel.send('Could not send a private message to specified user, I am probably blocked.');
        } finally {
            try {
                await member.ban({ reason, days: daysArgument});
                await msg.guild.unban(member.id); // Maybe put the unban in a seperate trycatch
                await bot.successReact(msg);                // or handle errors on it differently
                /*await bot.database.createUserBan(
                    member.user,
                    msg.author,                     // This needs to be updated with a softban DB endpoint.
                    msg.guild,
                    reason,
                    parsedTime ? Math.round(parsedTime.absolute / 1000) : null);*/
                    // await bot.createModLogEntry(msg, member, reason, 'ban',
                // banRecord.id)
            } catch (e) {
                await bot.failReact(msg);
                await msg.channel.send('Could not softban / unban specified user. Do I have enough permissions?');
            }
        }

        return;
    }
}

export = Softban;
