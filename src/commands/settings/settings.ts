import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { GuildConfig } from '../../database/database';
import * as Discord from 'discord.js';

class Settings implements Command {
    public usage = [
        'settings display - shows current state of settings',
        'settings prefix set <newPrefix> - sets a new prefix',
        'settings modlog <enable/disable> - enables or disables mod log feature',
        'settings modlog set channel <#channelName> - sets what channel mod logs are posted to',
        'settings holdingRoom <enable/disable> - enables or disables holding room feature',
        'settings holdingRoom set role <roleName> - sets the role assigned to users when holding time passes',
        // tslint:disable-next-line:max-line-length
        'settings holdingRoom set minutes <minutes> - sets how much minutes a new user has to wait before being allowed',
        'settings holdingRoom set channel <#channelName> - sets what channel welcome messages are posted to',
    ];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public run(bot: SafetyJim, msg: Discord.Message, args: string): boolean {
        let splitArgs = args.split(' ');
        if (!args || !['display', 'holdingRoom', 'prefix', 'modlog'].includes(splitArgs[0])) {
            return true;
        }

        if (splitArgs[0] === 'display') {
            this.handleSettingsDisplay(bot, msg);
            return;
        } else if (splitArgs[0] === 'modlog') {
            if (!splitArgs[1] || !['enable', 'disable', 'set'].includes(splitArgs[1])) {
                return true;
            }

            switch (splitArgs[1]) {
                case 'enable':
                case 'disable':
                    this.handleModLogSwitch(bot, msg, splitArgs[1] === 'enable');
                    break;
                case 'set':
                    if (splitArgs.length < 3 || splitArgs[2] !== 'channel') {
                        return true;
                    }

                    if (msg.mentions.channels.size === 0 ||
                        !Discord.MessageMentions.CHANNELS_PATTERN.test(splitArgs[3])) {
                        msg.channel.send('Invalid channel input, no changes were made!');
                        return;
                    }

                    let channel = msg.mentions.channels.first();

                    bot.log.info(`Updated channel for mod log in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                    bot.database.updateGuildConfig(msg.guild, { modLogChannelID: channel.id });
                    break;
            }

            return;
        } else if (splitArgs[0] === 'holdingRoom') {
            if (!splitArgs[1] || !['enable', 'disable', 'set'].includes(splitArgs[1])) {
                return true;
            }

            switch (splitArgs[1]) {
                case 'enable':
                case 'disable':
                    this.handleHoldingRoomSwitch(bot, msg, splitArgs[1] === 'enable');
                    break;
                case 'set':
                    if (splitArgs.length < 3 || !['role', 'minutes', 'channel', 'prefix'].includes(splitArgs[2])) {
                        return true;
                    }
                    this.handleHoldingRoomSet(bot, msg, splitArgs.slice(2));
                    break;
            }

            return;
        } else if (splitArgs[0] === 'prefix') {
            if (splitArgs[1] !== 'set' || splitArgs.length < 3) {
                return true;
            }

            let newPrefix = splitArgs[2];
            bot.createRegexForGuild(msg.guild.id, newPrefix);
            bot.database.updateGuildPrefix(msg.guild, newPrefix);
            bot.log.info(`Updated prefix for guild "${msg.guild}" with id: "${msg.guild.id} with "${newPrefix}"`);
        }
    }

    private getSettingsString(msg: Discord.Message, config: GuildConfig, prefix: string): string {
        let output = '';
        output += `Prefix: ${prefix}\n`;
        output += `Embed color: ${config.EmbedColor}\n`;

        if (config.ModLogActive === 0) {
            output += 'Mod Log: Disabled\n';
        } else {
            output += 'Mod Log: Enabled\n';
            output += `\tMod Log Channel: ${msg.guild.channels.get(config.ModLogChannelID).name}\n`;
        }

        if (config.HoldingRoomActive === 0) {
            output += 'Holding Room: Disabled\n';
        } else {
            output += 'Holding Room: Enabled\n';
            output += `\tHolding Room Channel: ${msg.guild.channels.get(config.HoldingRoomChannelID).name}\n`;
            output += `\tHolding Room Role: ${msg.guild.roles.get(config.HoldingRoomRoleID).name}\n`;
            output += `\tHolding Room Delay: ${config.HoldingRoomMinutes} minute(s)`;
        }

        return output;
    }

    private handleSettingsDisplay(bot: SafetyJim, msg: Discord.Message): void {
        Promise.all([bot.database.getGuildConfiguration(msg.guild), bot.database.getGuildPrefix(msg.guild)])
            .then((c) => this.getSettingsString(msg, c[0], c[1]))
            .then((s) => msg.channel.send(s, {code: 'http'}))
            .catch((e) => {
                msg.channel.send('There was an error while trying to display settings, this incident has been logged.');
                bot.log.error(`Could not display settings for guild: "${msg.guild.name}" with id: "${msg.guild.id}"`);
            });
    }

    private async handleModLogSwitch(bot: SafetyJim, msg: Discord.Message, enable: boolean): Promise<void> {
        let config = await bot.database.getGuildConfiguration(msg.guild);

        if (!enable) {
            if (config.ModLogActive === 0) {
                msg.channel.send('Mod log is already disabled silly.');
            } else {
                msg.channel.send('Disabled mod log.');
                bot.log.info(`Disabled mod log for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
                bot.database.updateGuildConfig(msg.guild, { modLog: false });
                return;
            }
        } else {
            if (config.ModLogActive === 1) {
            msg.channel.send('Mod log is already enabled silly.');
            } else {
                bot.database.updateGuildConfig(msg.guild, { modLog: true });
                bot.log.info(`Enabled mod log for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
                msg.channel.send('Enabled mod log.');
            }
        }
    }

    private async handleHoldingRoomSwitch(bot: SafetyJim, msg: Discord.Message, enable: boolean): Promise<void> {
        let config = await bot.database.getGuildConfiguration(msg.guild);

        if (!enable) {
            if (config.HoldingRoomActive === 0) {
                msg.channel.send('Holding room is already disabled silly.');
            } else {
                msg.channel.send('Disabled holding room.');
                bot.log.info(`Disabled holding room for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
                bot.database.updateGuildConfig(msg.guild, { holdingRoom: false });
                return;
            }
        }

        if (config.HoldingRoomActive === 1) {
            msg.channel.send('Holding room is already enabled silly.');
            return;
        }

        // We are only checking for role id because it is the only value that is null
        // at initialization of guild configs
        if (!config.HoldingRoomRoleID) {
            let prefix = await bot.database.getGuildPrefix(msg.guild);
            let output = '';
            output += 'Couldn\'t enable holding room because role is missing in your config!\n';
            output += `Try ${prefix + ' ' + 'settings holdingRoom set role <roleName>'}`;
            msg.channel.send(output);
        } else {
            bot.database.updateGuildConfig(msg.guild, { holdingRoom: true });
            bot.log.info(`Enabled holding room for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
            msg.channel.send('Enabled holding room.');
        }
    }

    private handleHoldingRoomSet(bot: SafetyJim, msg: Discord.Message, args: string[]): void {
        switch (args[0]) {
            case 'role':
                let roleName = args.slice(1).join(' ');
                let id = msg.guild.roles.filter((r) => r.name === roleName).array()[0].id;
                if (!roleName) {
                    msg.channel.send('Invalid role name, no changes were made!');
                    return;
                } else {
                    bot.log.info(`Updated role for holding room in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                    bot.database.updateGuildConfig(msg.guild, { holdingRoomRoleID: id });
                }
                break;
            case 'minutes':
                let input = args[1];
                let minute = parseInt(input);
                if (!minute) {
                    msg.channel.send('Invalid input in minutes field, no changes were made!');
                    return;
                } else {
                    // tslint:disable-next-line:max-line-length
                    bot.log.info(`Updated minutes for holding room in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                    bot.database.updateGuildConfig(msg.guild, {minutes: minute});
                }
                break;
            case 'channel':
                if (msg.mentions.channels.size === 0 || !Discord.MessageMentions.CHANNELS_PATTERN.test(args[1])) {
                    msg.channel.send('Invalid channel input, no changes were made!');
                    return;
                }

                let channel = msg.mentions.channels.first();

                bot.log.info(`Updated channel for holding room in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                bot.database.updateGuildConfig(msg.guild, { holdingRoomID: channel.id });
                break;
        }

        msg.channel.send('Updated guild configuration.');
    }
}

export = Settings;
