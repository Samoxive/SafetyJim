import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Unban implements Command {
    public usage = 'unban <tag> - unbans user with specified user tag (example#1998)';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        if (!args) {
            return true;
        }

        let unbanUsername = args;

        if (!msg.guild.me.hasPermission('BAN_MEMBERS')) {
            bot.failReact(msg);
            msg.channel.send('I do not have enough permissions to do that!');
            return;
        }
        msg.guild.fetchBans()
                 .then((bans) => bans.find('tag', unbanUsername))
                 .then((bannee) => {
                     if (!bannee) {
                         this.userNotFound(bot, msg, args);
                     } else {
                         bot.successReact(msg);
                         msg.guild.unban(bannee.id);
                         bot.database.updateBanRecordWithID(bannee.id, msg.guild.id);
                     }
                 });
        return;
    }

    public userNotFound(bot: SafetyJim, msg: Discord.Message, username: string): void {
        bot.failReact(msg);
        msg.channel.send(`Could not find a banned user called \`${username}\`!`);
    }
}

export = Unban;
