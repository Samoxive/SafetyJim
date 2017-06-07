import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import * as Discord from 'discord.js';

class Warn implements Command {
    public usage = 'warn @user [reason] - Warn the user with the specified reason';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');
        args = splitArgs.slice(1).join(' ');

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        let member = msg.guild.member(msg.mentions.users.first());

        let reason = args || 'No reason specified';

        bot.log.info(`Warned user "${member.user.tag}" in "${msg.guild.name}".`);

        // tslint:disable-next-line:max-line-length
        member.send(`**Time out!** You have been warned in ${msg.guild.name}.\n\n**Warned by:** ${msg.author.tag}\n\n**Reason:** ${reason}`);

        bot.database.createUserWarn(member.user, msg.author, msg.guild, reason);
        this.createModLogEntry(bot, msg, member, reason);
        return;
    }

    private async createModLogEntry(bot: SafetyJim, msg: Discord.Message,
                                    member: Discord.GuildMember, reason: string): Promise<void> {
        let db = await bot.database.getGuildConfiguration(msg.guild);
        let prefix = await bot.database.getGuildPrefix(msg.guild);

        if (!db  || !db.ModLogActive) {
            return;
        }

        if (!bot.client.channels.has(db.ModLogChannelID) ||
            bot.client.channels.get(db.ModLogChannelID).type !== 'text') {
            // tslint:disable-next-line:max-line-length
            msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
            return;
        }

        let logChannel = bot.client.channels.get(db.ModLogChannelID) as Discord.TextChannel;

        let embed = {
            color: 0xFFEB00, // yellow
            fields: [
                { name: 'Action:', value: 'Warning', inline: false },
                { name: 'User:', value: member.user.tag, inline: false },
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Responsible Moderator:', value: msg.author.tag, inline: false },
            ],
            timestamp: new Date(),
        };

        logChannel.send({ embed });

        return;
    }

}
module.exports = Warn;
