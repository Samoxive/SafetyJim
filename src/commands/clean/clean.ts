import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
class Clean implements Command {
    public usage = 'ping - pong';
    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}
    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let newArgs = args.split(' ');
        let deleteAmount;
        if (!parseInt(newArgs[0])) {
            deleteAmount = 2;
        } else {
            deleteAmount = parseInt(newArgs[0]);
        }

        if (deleteAmount && deleteAmount > 99) {
            msg.channel.send('You can\'t delete more than 100 messages.')
            bot.failReact(msg)
        }
        if (!newArgs[1]) {
            msg.channel.bulkDelete(deleteAmount + 1);
            bot.failReact(msg);
            return;
        }
        if (newArgs[1].match(Discord.MessageMentions.USERS_PATTERN)) {
            msg.channel.fetchMessages({ limit: 100 }).then((messages) => {
                const newMessages = messages.filterArray((m) => m.author.id === msg.mentions.users.first().id)
                    .slice(0, deleteAmount);
                msg.channel.bulkDelete(newMessages);
            });
        }
        if (newArgs[1].toLowerCase() === 'bot') {
            msg.channel.fetchMessages({ limit: 100 }).then((messages) => {
                const newMessages = messages.filterArray((m) => m.author.bot)
                    .slice(0, deleteAmount);
                msg.channel.bulkDelete(newMessages);
            });
        }
        return;
    }
}
export = Clean;