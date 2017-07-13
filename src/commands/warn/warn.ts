import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { GuildConfig } from '../../database/database';
import * as Discord from 'discord.js';

class Warn implements Command {
    public usage = 'warn @user [reason] - warn the user with the specified reason';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        args = splitArgs.slice(1).join(' ');

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        await bot.client.fetchUser(msg.mentions.users.first().id);
        let member = await msg.guild.fetchMember(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            await bot.failReact(msg);
            await msg.channel.send('You can\'t warn yourself, dummy!');
            return;
        }

        let reason = args || 'No reason specified';

        bot.log.info(`Warned user "${member.user.tag}" in "${msg.guild.name}".`);

        let EmbedColor = await bot.database.getSetting(msg.guild, 'EmbedColor');
        let embed = {
            title: `Warned in ${msg.guild.name}`,
            color: parseInt(EmbedColor, 16),
            fields: [{ name: 'Reason:', value: reason, inline: false }],
            description: `You were warned in ${msg.guild.name}.`,
            footer: { text: `Warned by: ${msg.author.tag} (${msg.author.id})`},
            timestamp: new Date(),
        };

        try {
            await member.send({ embed });
        } catch (e) {
            await msg.channel.send('Could not send a warning to specified user via private message!');
        } finally {
            bot.successReact(msg);
        }

        await this.createModLogEntry(bot, msg, member, reason);
        await bot.database.createUserWarn(member.user, msg.author, msg.guild, reason);

        return;
    }
    private async createModLogEntry(bot: SafetyJim, msg: Discord.Message,
                                    member: Discord.GuildMember, reason: string): Promise<void> {
        let ModLogActive = await bot.database.getSetting(msg.guild, 'ModLogActive');
        let prefix = await bot.database.getSetting(msg.guild, 'Prefix');

        if (!ModLogActive || ModLogActive === 'false') {
            return;
        }

        let ModLogChannelID = await bot.database.getSetting(msg.guild, 'ModLogChannelID');

        if (!bot.client.channels.has(ModLogChannelID) ||
            bot.client.channels.get(ModLogChannelID).type !== 'text') {
            // tslint:disable-next-line:max-line-length
            await msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
            return;
        }

        let logChannel = bot.client.channels.get(ModLogChannelID) as Discord.TextChannel;

        let embed = {
            color: 0xFFEB00, // yellow
            fields: [
                { name: 'Action:', value: 'Warning', inline: false },
                { name: 'User:', value: `${member.user.tag} (${member.id})`, inline: false },
                { name: 'Reason:', value: reason, inline: false },
                { name: 'Responsible Moderator:', value: `${msg.author.tag} (${msg.author.id})`, inline: false },
            ],
            timestamp: new Date(),
        };

        await logChannel.send({ embed });

        return;
    }

}
module.exports = Warn;
