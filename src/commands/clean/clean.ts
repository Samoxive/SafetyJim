import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';
class Clean implements Command {
    public usage = [
        'clean - deletes one message',
        'clean <number> @user - deletes number of messages from specified user',
        'clean <number> bot - deletes number of messages sent from bots'];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let newArgs = args.split(' ');
        let deleteAmount = parseInt(newArgs[0]);

        if (!msg.guild.me.hasPermission('MANAGE_MESSAGES')) {
            await bot.failReact(msg);
            await msg.channel.send('I don\'t have enough permissions to do that!');
            return;
        }

        if (newArgs[0] === '' && newArgs.length === 1) {
            await msg.channel.bulkDelete(2);
            return;
        }

        if (isNaN(deleteAmount)) {
            return true;
        }

        if (deleteAmount < 1) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t delete zero or negative messages.');
            return;
        }

        if (deleteAmount > 100) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t delete more than 100 messages.');
            return;
        }

        if (!newArgs[1]) {
            await msg.channel.bulkDelete(deleteAmount + 1);
            return;
        }

        if (!newArgs[1].match(Discord.MessageMentions.USERS_PATTERN) &&
            newArgs[1].toLowerCase() !== 'bot') {
                await bot.failReact(msg);
                return true;
        }

        if (newArgs[1].match(Discord.MessageMentions.USERS_PATTERN)) {
            let deleteUser = msg.mentions.users.first();

            if (deleteUser.id === msg.author.id) {
                deleteAmount++;
            }

            let messages = await msg.channel.fetchMessages({ limit: 100 });
            const newMessages = messages.filterArray((m) => m.author.id === msg.mentions.users.first().id)
                .slice(0, deleteAmount);

            if (deleteAmount === 1) {
                newMessages[0].delete();
            } else {
                await msg.channel.bulkDelete(newMessages);
            }

            await bot.successReact(msg);
            return;
        }

        if (newArgs[1].toLowerCase() === 'bot') {
            let messages = await msg.channel.fetchMessages({ limit: 100 });
            const newMessages = messages.filterArray((m) => m.author.bot)
                .slice(0, deleteAmount);

            if (deleteAmount === 1) {
                newMessages[0].delete();
            } else {
                await msg.channel.bulkDelete(newMessages);
            }

            await bot.successReact(msg);
            return;
        }
        return;
    }
}
export = Clean;
