import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';
import { Warns } from '../../database/models/Warns';

class Warn implements Command {
    public usage = 'warn @user [reason] - warn the user with the specified reason';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        args = splitArgs.slice(1).join(' ');

        if (!msg.member.hasPermission('KICK_MEMBERS')) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
            return;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        await shard.client.fetchUser(msg.mentions.users.first().id, true);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, 'You can\'t warn yourself, dummy!');
            return;
        }

        let reason = args || 'No reason specified';

        shard.log.info(`Warned user "${member.user.tag}" in "${msg.guild.name}".`);

        let embed = {
            title: `Warned in ${msg.guild.name}`,
            color: 0x4286f4,
            fields: [{ name: 'Reason:', value: reason, inline: false }],
            description: `You were warned in ${msg.guild.name}.`,
            footer: { text: `Warned by: ${msg.author.tag} (${msg.author.id})`},
            timestamp: new Date(),
        };

        try {
            await member.send({ embed });
        } catch (e) {
            await Utils.sendMessage(msg.channel, 'Could not send a warning to specified user via private message!');
        } finally {
            Utils.successReact(jim, msg);
        }

        let now = Math.round((new Date()).getTime() / 1000);
        let warnRecord = await Warns.create<Warns>({
            userid: member.id,
            moderatoruserid: msg.author.id,
            guildid: msg.guild.id,
            warntime: now,
            reason,
        });

        await Utils.createModLogEntry(shard, msg, member, reason, 'warn', warnRecord.id);
        await Utils.deleteCommandMessage(jim, msg);
        return;
    }
}
module.exports = Warn;
