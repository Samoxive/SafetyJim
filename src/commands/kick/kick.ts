import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { GuildConfig } from '../../database/database';
import * as Discord from 'discord.js';

class Kick implements Command {
    public usage = 'kick @user [reason] - Kicks the user with the specified reason';

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');
        args = splitArgs.slice(1).join(' ');

        if (msg.mentions.users.size === 0 ||
            !splitArgs[0].match(Discord.MessageMentions.USERS_PATTERN)) {
            return true;
        }

        if (!msg.guild.me.hasPermission('KICK_MEMBERS')) {
            msg.channel.send('I don\'t have enough permissions to do that!');
        }

        let member = msg.guild.member(msg.mentions.users.first());

        if (member.id === msg.author.id) {
            msg.channel.send('You can\'t kick yourself, dummy!');
            return;
        }

        if (!member || !member.kickable || msg.member.highestRole.comparePositionTo(member.highestRole) <= 0) {
            msg.channel.send('The specified member is not kickable.');
            return;
        }

        let reason = args || 'No reason specified';

        bot.log.info(`Kicked user "${member.user.tag}" in "${msg.guild.name}".`);
        // Audit log compatibility :) (Known Caveat: sometimes reason won't appear, or add if reason has symbols.)
        // tslint:disable-next-line:max-line-length
        bot.database.getGuildConfiguration(msg.guild)
                    .then((config) => {
                        this.kickUser(msg, member, reason, config);
                        this.createModLogEntry(bot, msg, member, reason, config);
                    });

        bot.database.createUserKick(member.user, msg.author, msg.guild, reason);
        return;
    }

    private async kickUser(msg: Discord.Message, member: Discord.GuildMember,
                           reason: string, config: GuildConfig): Promise<void> {
        let embed = {
            title: `Kicked from ${msg.guild.name}`,
            color: parseInt(config.EmbedColor, 16),
            fields: [{ name: 'Reason:', value: reason, inline: false }],
            description: `You were kicked from ${msg.guild.name}.`,
            footer: { text: `Kicked by: ${msg.author.tag}`},
            timestamp: new Date(),
        };

        member.send({ embed })
              .then(() => {
                  msg.react('322352183226007554');
                  member.kick(reason);
                })
              .catch(() => {
                  msg.react('322352183226007554');
                  member.kick(reason);
              });
    }

    private async createModLogEntry(bot: SafetyJim, msg: Discord.Message,
                                    member: Discord.GuildMember, reason: string, config: GuildConfig): Promise<void> {
        let prefix = await bot.database.getGuildPrefix(msg.guild);

        if (!config  || !config.ModLogActive) {
            return;
        }

        if (!bot.client.channels.has(config.ModLogChannelID) ||
            bot.client.channels.get(config.ModLogChannelID).type !== 'text') {
            // tslint:disable-next-line:max-line-length
            msg.channel.send(`Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
            return;
        }

        let logChannel = bot.client.channels.get(config.ModLogChannelID) as Discord.TextChannel;

        let embed = {
            color: 0xFF9900, // orange
            fields: [
                { name: 'Action:', value: 'Kick', inline: false },
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
module.exports = Kick;
