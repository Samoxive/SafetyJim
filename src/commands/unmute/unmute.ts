import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';
import { Mutes } from '../../database/models/Mutes';

class Unmute implements Command {
    public usage = 'unmute @user - unmutes specified user';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');

        if (!msg.member.hasPermission('MANAGE_ROLES')) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
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
            await Utils.failReact(jim, msg);
            // tslint:disable-next-line:max-line-length
            await Utils.sendMessage(msg.channel, 'Could not find a Muted role, please create one yourself or mute a user to automatically setup one.');
            return;
        }

        await shard.client.fetchUser(msg.mentions.users.first().id, true);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        await member.removeRole(role);
        await Utils.successReact(jim, msg);
        await Mutes.update<Mutes>({ unmuted: true }, {
            where: {
                userid: member.id,
                guildid: msg.guild.id,
            },
        });
    }
}

export = Unmute;
