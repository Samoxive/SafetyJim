import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
class Clean implements Command {
    public usage = [
        'clean - deletes one message',
        'clean <number> @user - deletes number of messages from specified user',
        'clean <number> bot - deletes number of messages sent from bots'];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let newArgs = args.split(' ');
        let deleteAmount = parseInt(newArgs[0]);

        if (newArgs[0] === '' && newArgs.length === 1) {
            bot.successReact(msg);
            msg.channel.bulkDelete(2);
            return;
        }

        if (isNaN(deleteAmount)) {
            return true;
        }

        if (deleteAmount < 1) {
            msg.channel.send('You can\'t delete zero or negative messages.');
            bot.failReact(msg);
            return;
        }

        if (deleteAmount > 100) {
            msg.channel.send('You can\'t delete more than 100 messages.');
            bot.failReact(msg);
            return;
        }

        if (!newArgs[1]) {
            bot.successReact(msg);
            msg.channel.bulkDelete(deleteAmount + 1);
            return;
        }

        if (!newArgs[1].match(Discord.MessageMentions.USERS_PATTERN) &&
            newArgs[1].toLowerCase() !== 'bot') {
                bot.failReact(msg);
                return true;
        }

        if (newArgs[1].match(Discord.MessageMentions.USERS_PATTERN)) {
            bot.successReact(msg);
            let deleteUser = msg.mentions.users.first();

            if (deleteUser.id === msg.author.id) {
                deleteAmount++;
            }

            msg.channel.fetchMessages({ limit: 100 }).then((messages) => {
                const newMessages = messages.filterArray((m) => m.author.id === msg.mentions.users.first().id)
                    .slice(0, deleteAmount);

                if (deleteAmount === 1) {
                    newMessages[0].delete();
                } else {
                    msg.channel.bulkDelete(newMessages);
                }
            });
        }

        if (newArgs[1].toLowerCase() === 'bot') {
            bot.successReact(msg);

            msg.channel.fetchMessages({ limit: 100 }).then((messages) => {
                const newMessages = messages.filterArray((m) => m.author.bot)
                    .slice(0, deleteAmount);

                if (deleteAmount === 1) {
                    newMessages[0].delete();
                } else {
                    msg.channel.bulkDelete(newMessages);
                }
            });
        }
        return;
    }
}
export = Clean;
