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
            await this.createModLogEntry(bot, msg, 1, undefined);
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
            await this.createModLogEntry(bot, msg, deleteAmount, undefined);
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
            await this.createModLogEntry(bot, msg, deleteAmount, msg.mentions.members.first());
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

            await this.createModLogEntry(bot, msg, deleteAmount, 'bot');
            await bot.successReact(msg);
            return;
        }
        return;
    }

    private async createModLogEntry(bot: SafetyJim, msg: Discord.Message, count: number,
                                    member: Discord.GuildMember | string | void) {
        let db = await bot.database.getGuildConfiguration(msg.guild);
        let prefix = await bot.database.getGuildPrefix(msg.guild);

        if (!db || !db.ModLogActive) {
            return;
        }

        if (!bot.client.channels.has(db.ModLogChannelID) ||
            bot.client.channels.get(db.ModLogChannelID).type !== 'text') {
            // tslint:disable-next-line:max-line-length
            await msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
            return;
        }

        let logChannel = bot.client.channels.get(db.ModLogChannelID) as Discord.TextChannel;
        let userString;

        if (member == null) {
            userString = 'None';
        } else if (member === 'bot') {
            userString = 'Bots';
        } else {
            member = member as Discord.GuildMember;
            userString = `${member.user.tag} (${member.id})`;
        }

        let embed = {
            color: 0x42f4b3, // white
            fields: [
                { name: 'Action:', value: 'Clean' },
                { name: 'User:', value: userString, inline: false },
                { name: 'Count:', value: count, inline: false },
                { name: 'Responsible Moderator:', value: `${msg.author.tag} (${msg.author.id})`, inline: false },
            ],
            timestamp: new Date(),
        };

        await logChannel.send({ embed });
        return;
    }
}
export = Clean;
