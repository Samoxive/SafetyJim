import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import * as Discord from 'discord.js';
import { Bans } from '../../database/models/Bans';

class Unban implements Command {
    public usage = 'unban <tag> - unbans user with specified user tag (example#1998)';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        if (!msg.member.hasPermission('BAN_MEMBERS')) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, 'You don\'t have enough permissions to execute this command!');
            return;
        }

        if (!args) {
            return true;
        }

        let unbanUsername = args;

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, 'I do not have enough permissions to do that!');
            return;
        }

        let bannee = await msg.guild.fetchBans().then((bans) => bans.find('tag', unbanUsername));

        if (!bannee) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, `Could not find a banned user called \`${args}\`!`);
        } else {
            await Utils.successReact(jim, msg);
            await msg.guild.unban(bannee.id);
            await Bans.update<Bans>({ unbanned: true }, {
                where: {
                    userid: bannee.id,
                    guildid: msg.guild.id,
                },
            });
        }

        
        await Utils.deleteCommandMessage(jim, msg);
        return;
    }
}

export = Unban;
