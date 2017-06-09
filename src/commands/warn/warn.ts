import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { GuildConfig } from '../../database/database';
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

        if (member.id === msg.author.id) {
            bot.failReact(msg);
            msg.channel.send('You can\'t warn yourself, dummy!');
            return;
        }

        let reason = args || 'No reason specified';

        bot.log.info(`Warned user "${member.user.tag}" in "${msg.guild.name}".`);

        bot.database.getGuildConfiguration(msg.guild)
                    .then((config) => {
                        this.warnUser(bot, msg, member, reason, config);
                        this.createModLogEntry(bot, msg, member, reason, config);
                        bot.successReact(msg);
                    });

        bot.database.createUserWarn(member.user, msg.author, msg.guild, reason);
        return;
    }

    private async warnUser(bot: SafetyJim, msg: Discord.Message, member: Discord.GuildMember,
                           reason: string, config: GuildConfig): Promise<void> {
        let embed = {
            title: `Warned in ${msg.guild.name}`,
            color: parseInt(config.EmbedColor, 16),
            fields: [{ name: 'Reason:', value: reason, inline: false }],
            description: `You were warned in ${msg.guild.name}.`,
            footer: { text: `Warned by: ${msg.author.tag}`},
            timestamp: new Date(),
        };

        member.send({ embed })
              .catch(() => {
                  bot.log.info(`Could not send a warning to user "${member.user.tag}" in guild "${msg.guild.name}".`);
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
