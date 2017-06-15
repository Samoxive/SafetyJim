import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Unban implements Command {
    public usage = 'unban <username> - Unbans user with specified username';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        if (!args) {
            return true;
        }

        let unbanUsername = args.toLowerCase();

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            bot.failReact(msg);
            msg.channel.send('I do not have enough permissions to do that!');
            return;
        }
        msg.guild.fetchBans()
                 .then((bans) => bans.find('username', unbanUsername))
                 .then((bannee) => {
                     if (!bannee) {
                         this.userNotFound(bot, msg, args);
                     } else {
                         bot.successReact(msg);
                         msg.guild.unban(bannee.id);
                         bot.database.updateBanRecordWithID(bannee.id, msg.guild.id);
                         msg.channel.send(`Unbanned user ${bannee.tag}.`);
                     }
                 });
        return;
    }

    public userNotFound(bot: SafetyJim, msg: Discord.Message, username: string): void {
        bot.failReact(msg);
        msg.channel.send(`Could not find a banned user called ${username}!`);
    }
}

export = Unban;
