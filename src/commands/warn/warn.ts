import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';
import { Warns } from '../../database/models/Warns';

class Warn implements Command {
    public usage = 'warn @user [reason] - warn the user with the specified reason';

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

        await bot.client.fetchUser(msg.mentions.users.first().id);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t warn yourself, dummy!');
            return;
        }

        let reason = args || 'No reason specified';

        bot.log.info(`Warned user "${member.user.tag}" in "${msg.guild.name}".`);

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
            await msg.channel.send('Could not send a warning to specified user via private message!');
        } finally {
            bot.successReact(msg);
        }

        let now = Math.round((new Date()).getTime() / 1000);
        let warnRecord = await Warns.create<Warns>({
            userid: member.id,
            moderatoruserid: msg.author.id,
            guildid: msg.guild.id,
            warntime: now,
            reason,
        });

        await bot.createModLogEntry(msg, member, reason, 'warn', warnRecord.id);
        await bot.deleteCommandMessage(msg);
        return;
    }
}
module.exports = Warn;
