import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';
import { Kicks } from '../../database/models/Kicks';

class Kick implements Command {
    public usage = 'kick @user [reason] - kicks the user with the specified reason';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        args = splitArgs.slice(1).join(' ');

        if (!msg.member.hasPermission('KICK_MEMBERS')) {
            await bot.failReact(msg);
            await msg.channel.send('You don\'t have enough permissions to execute this command!');
            return;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('KICK_MEMBERS')) {
            await bot.failReact(msg);
            await msg.channel.send('I don\'t have enough permissions to do that!');
            return;
        }

        let member = msg.guild.member(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t kick yourself, dummy!');
            return;
        }

        if (!member || !member.kickable || msg.member.highestRole.comparePositionTo(member.highestRole) <= 0) {
            await bot.failReact(msg);
            await msg.channel.send('The specified member is not kickable.');
            return;
        }

        let reason = args || 'No reason specified';

        let embed = {
            title: `Kicked from ${msg.guild.name}`,
            color: 0x4286f4,
            fields: [{ name: 'Reason:', value: reason, inline: false }],
            description: `You were kicked from ${msg.guild.name}.`,
            footer: { text: `Kicked by: ${msg.author.tag} (${msg.author.id})`},
            timestamp: new Date(),
        };

        try {
            await member.send({ embed });
        } catch (e) {
            await msg.channel.send('Could not send a private message to specified user, I am probably blocked.');
        } finally {
            try {
                let auditLogReason = `Kicked by ${msg.author.tag} (${msg.author.id}) - ${reason}`;
                await member.kick(auditLogReason);
                await bot.successReact(msg);

                let now = Math.round((new Date()).getTime() / 1000);
                let kickRecord = await Kicks.create<Kicks>({
                    userid: member.id,
                    moderatoruserid: msg.author.id,
                    guildid: msg.guild.id,
                    kicktime: now,
                    reason,
                });

                await bot.createModLogEntry(msg, member, reason, 'kick', kickRecord.id);
            } catch (e) {
                await bot.failReact(msg);
                await msg.channel.send('Could not kick specified user. Do I have enough permissions?');
            }
        }
    }
}
module.exports = Kick;
