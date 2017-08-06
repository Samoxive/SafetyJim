import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Mutes } from '../../database/models/Mutes';

class Unmute implements Command {
    public usage = 'unmute @user - unmutes specified user';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (!msg.member.hasPermission('MANAGE_ROLES')) {
            await bot.failReact(msg);
            await msg.channel.send('You don\'t have enough permissions to execute this command!');
            return;
        }

        if (!args) {
            return true;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        let role = msg.guild.roles.find('name', 'Muted');

        if (!role) {
            await bot.failReact(msg);
            // tslint:disable-next-line:max-line-length
            await msg.channel.send('Could not find a Muted role, please create one yourself or mute a user to automatically setup one.');
            return;
        }

        await bot.client.fetchUser(msg.mentions.users.first().id);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        await member.removeRole(role);
        await bot.successReact(msg);
        await Mutes.update<Mutes>({ unmuted: true }, {
            where: {
                userid: member.id,
                guildid: msg.guild.id,
            },
        });
    }
}

export = Unmute;
