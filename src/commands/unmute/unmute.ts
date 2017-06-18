import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Unmute implements Command {
    public usage = 'unmute @user - Unmutes specified user';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');

        if (!args) {
            return true;
        }

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        let role = msg.guild.roles.find('name', 'Muted');

        if (!role) {
            bot.failReact(msg);
            // tslint:disable-next-line:max-line-length
            msg.channel.send('Could not find a Muted role, please create one yourself or mute a user to automatically setup one.');
            return;
        }

        let member = msg.mentions.members.first();

        member.removeRole(role)
              .then(() => {
                  bot.database.updateMuteRecordWithID(member.user.id, msg.guild.id);
                  bot.successReact(msg);
              })
              .catch(() => {
                  bot.failReact(msg);
                  msg.channel.send('Could not unmute specified user!');
              });
    }
}

export = Unmute;
