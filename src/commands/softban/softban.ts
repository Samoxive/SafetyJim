import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Softbans } from '../../database/models/Softbans';

class Softban implements Command {
    public usage = 'softban @user [reason] | [messages to delete (days)] - softbans the user with the specified args.';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (!msg.member.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
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
            await bot.sendMessage(msg.channel, 'You can\'t softban yourself, dummy!');
            return false;
        }

        if (!member.bannable || !msg.guild.me.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            await bot.sendMessage(msg.channel, 'I don\'t have enough permissions to do that!');
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
                await bot.failReact(msg);
                return true;
            }
            daysArgument = parseInt(daysArgument);

            if (daysArgument < 1 || daysArgument > 7) {
                await bot.failReact(msg);
                await bot.sendMessage(msg.channel, 'The amount of days must be between 1 and 7.');
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
            // tslint:disable-next-line:max-line-length
            await bot.sendMessage(msg.channel, 'Could not send a private message to specified user, I am probably blocked.');
        } finally {
            try {
                await member.ban({ reason, days: daysArgument});
                await msg.guild.unban(member.id); // Maybe put the unban in a seperate trycatch
                await bot.successReact(msg);                // or handle errors on it differently

                let now = Math.round((new Date()).getTime() / 1000);
                await Softbans.create<Softbans>({
                    userid: member.id,
                    moderatoruserid: msg.author.id,
                    guildid: msg.guild.id,
                    softbantime: now,
                    deletedays: daysArgument,
                    reason,
                });
            } catch (e) {
                await bot.failReact(msg);
                // tslint:disable-next-line:max-line-length
                await bot.sendMessage(msg.channel, 'Could not softban / unban specified user. Do I have enough permissions?');
            }
        }

        return;
    }
}

export = Softban;
