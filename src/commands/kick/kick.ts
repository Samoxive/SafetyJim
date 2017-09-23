import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';
import { Kicks } from '../../database/models/Kicks';

class Kick implements Command {
    public usage = 'kick @user [reason] - kicks the user with the specified reason';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        args = splitArgs.slice(1).join(' ');

        if (!msg.member.hasPermission('KICK_MEMBERS')) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
            return;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('KICK_MEMBERS')) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'I don\'t have enough permissions to do that!');
            return;
        }

        let member = msg.guild.member(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'You can\'t kick yourself, dummy!');
            return;
        }

        if (!member || !member.kickable || msg.member.highestRole.comparePositionTo(member.highestRole) <= 0) {
            await Utils.failReact(msg);
            await Utils.sendMessage(msg.channel, 'The specified member is not kickable.');
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
            // tslint:disable-next-line:max-line-length
            await Utils.sendMessage(msg.channel, 'Could not send a private message to specified user, I am probably blocked.');
        } finally {
            try {
                await member.kick(reason);
                await Utils.successReact(msg);

                let now = Math.round((new Date()).getTime() / 1000);
                let kickRecord = await Kicks.create<Kicks>({
                    userid: member.id,
                    moderatoruserid: msg.author.id,
                    guildid: msg.guild.id,
                    kicktime: now,
                    reason,
                });

                await Utils.createModLogEntry(msg, member, reason, 'kick', kickRecord.id);
            } catch (e) {
                await Utils.failReact(msg);
                await Utils.sendMessage(msg.channel, 'Could not kick specified user. Do I have enough permissions?');
            }
        }

        await Utils.deleteCommandMessage(msg);
        return;
    }
}
module.exports = Kick;
