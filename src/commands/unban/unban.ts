import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
import { Bans } from '../../database/models/Bans';

class Unban implements Command {
    public usage = 'unban <tag> - unbans user with specified user tag (example#1998)';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        if (!msg.member.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            await msg.channel.send('You don\'t have enough permissions to execute this command!');
            return;
        }

        if (!args) {
            return true;
        }

        let unbanUsername = args;

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            await bot.failReact(msg);
            await msg.channel.send('I do not have enough permissions to do that!');
            return;
        }

        let bannee = await msg.guild.fetchBans().then((bans) => bans.find('tag', unbanUsername));

        if (!bannee) {
            await bot.failReact(msg);
            await msg.channel.send(`Could not find a banned user called \`${args}\`!`);
        } else {
            await bot.successReact(msg);
            await msg.guild.unban(bannee.id);
            await Bans.update<Bans>({ unbanned: true }, {
                where: {
                    userid: bannee.id,
                    guildid: msg.guild.id,
                },
            });
        }

        return;
    }
}

export = Unban;
