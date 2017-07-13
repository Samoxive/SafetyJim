import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { GuildConfig } from '../../database/database';
import * as Discord from 'discord.js';

class Settings implements Command {
    public usage = [
        'settings display - shows current state of settings',
        'settings embedColor set <newColor> - sets the embed color to a different color (ex. 2D7FFF)',
        'settings prefix set <newPrefix> - sets a new prefix',
        'settings modlog <enable/disable> - enables or disables mod log feature',
        'settings modlog set channel <#channelName> - sets what channel mod logs are posted to',
        'settings holdingRoom <enable/disable> - enables or disables holding room feature',
        'settings holdingRoom set role <roleName> - sets the role assigned to users when holding time passes',
        // tslint:disable-next-line:max-line-length
        'settings holdingRoom set minutes <minutes> - sets how much minutes a new user has to wait before being allowed',
        'settings holdingRoom set channel <#channelName> - sets what channel welcome messages are posted to',
        // tslint:disable-next-line:max-line-length
        'settings holdingRoom set message <message> - sets the message new members get mentioned with in the holdingroom (variables: $user, $guild and $minutes)',
    ];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        if (!args || !['display', 'holdingRoom', 'prefix', 'modlog', 'embedColor'].includes(splitArgs[0])) {
            return true;
        }

        if (splitArgs[0] === 'display') {
            await bot.successReact(msg);
            await this.handleSettingsDisplay(bot, msg);
            return;
        }

        if (!msg.member.hasPermission('ADMINISTRATOR')) {
            await bot.failReact(msg);
            await msg.author.send('You don\'t have enough permissions to modify guild settings!');
            return;
        }

        if (splitArgs[0] === 'modlog') {
            if (!splitArgs[1] || !['enable', 'disable', 'set'].includes(splitArgs[1])) {
                return true;
            }

            switch (splitArgs[1]) {
                case 'enable':
                case 'disable':
                    await this.handleModLogSwitch(bot, msg, splitArgs[1] === 'enable');
                    break;
                case 'set':
                    if (splitArgs.length < 3 || splitArgs[2] !== 'channel') {
                        return true;
                    }

                    if (msg.mentions.channels.size === 0 ||
                        !Discord.MessageMentions.CHANNELS_PATTERN.test(splitArgs[3])) {
                        await bot.failReact(msg);
                        await msg.channel.send('Invalid channel input, no changes were made!');
                        return;
                    }

                    let channel = msg.mentions.channels.first();

                    await bot.successReact(msg);
                    // tslint:disable-next-line:max-line-length
                    await bot.log.info(`Updated channel for mod log in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                    await bot.database.updateSettings(msg.guild, 'ModLogChannelID', channel.id);
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
                    await this.handleHoldingRoomSwitch(bot, msg, splitArgs[1] === 'enable');
                    break;
                case 'set':
                    if (splitArgs.length < 3 || !['role', 'minutes', 'channel', 'message'].includes(splitArgs[2])) {
                        return true;
                    }

                    await this.handleHoldingRoomSet(bot, msg, splitArgs.slice(2));
                    break;
            }

            return;
        } else if (splitArgs[0] === 'prefix') {
            if (splitArgs[1] !== 'set' || splitArgs.length < 3) {
                return true;
            }

            let newPrefix = splitArgs[2];

            await bot.successReact(msg);
            await bot.createRegexForGuild(msg.guild.id, newPrefix);
            await bot.database.updateSettings(msg.guild, 'Prefix', newPrefix);
            bot.log.info(`Updated prefix for guild "${msg.guild}" with id: "${msg.guild.id} with "${newPrefix}"`);
        } else if (splitArgs[0] === 'embedColor') {
            if (splitArgs[1] !== 'set' || splitArgs.length < 3) {
                return true;
            }

            let newColor = splitArgs[2];
            let newColorParsed = parseInt(newColor, 16);
            if (newColor.length !== 6 || isNaN(newColorParsed)) {
                await bot.failReact(msg);
                await msg.channel.send('Invalid color input, try a six digit hexadecimal number.');
                return;
            }

            await bot.successReact(msg);
            await bot.database.updateSettings(msg.guild, 'EmbedColor', newColor.toUpperCase());
            bot.log.info(`Updated embed color for guild "${msg.guild}" with id: "${msg.guild.id} with "${newColor}"`);
        }
    }

    private async getSettingsString(bot: SafetyJim, msg: Discord.Message): Promise<string> {
        let config = await bot.database.getGuildSettings(msg.guild);
        let output = '';
        output += `Prefix: ${config.get('Prefix')}\n`;
        output += `Embed color: #${config.get('EmbedColor')}\n`;

        if (config.get('ModLogActive') === 'false') {
            output += 'Mod Log: Disabled\n';
        } else {
            output += 'Mod Log: Enabled\n';
            output += `\tMod Log Channel: ${msg.guild.channels.get(config.get('ModLogChannelID')).name}\n`;
        }

        if (config.get('HoldingRoomActive') === 'false') {
            output += 'Holding Room: Disabled\n';
        } else {
            output += 'Holding Room: Enabled\n';
            output += `\tHolding Room Channel: ${msg.guild.channels.get(config.get('HoldingRoomChannelID')).name}\n`;
            output += `\tHolding Room Role: ${msg.guild.roles.get(config.get('HoldingRoomRoleID')).name}\n`;
            output += `\tHolding Room Delay: ${config.get('HoldingRoomMinutes')} minute(s)`;
        }

        return output;
    }

    private async handleSettingsDisplay(bot: SafetyJim, msg: Discord.Message): Promise<void> {
        let settingsString = await this.getSettingsString(bot, msg);

        await bot.successReact(msg);
        await msg.channel.send(settingsString, { code: 'http' });
    }

    private async handleModLogSwitch(bot: SafetyJim, msg: Discord.Message, enable: boolean): Promise<void> {
        let ModLogActive = await bot.database.getSetting(msg.guild, 'ModLogActive');

        if (!enable) {
            if (ModLogActive === 'false') {
                await bot.failReact(msg);
                await msg.channel.send('Mod log is already disabled silly.');
            } else {
                await bot.successReact(msg);
                bot.log.info(`Disabled mod log for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
                await bot.database.updateSettings(msg.guild, 'ModLogActive', 'false');
                return;
            }
        } else {
            if (ModLogActive === 'true') {
                await bot.failReact(msg);
                await msg.channel.send('Mod log is already enabled silly.');
            } else {
                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'ModLogActive', 'true');
                bot.log.info(`Enabled mod log for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
            }
        }
    }

    private async handleHoldingRoomSwitch(bot: SafetyJim, msg: Discord.Message, enable: boolean): Promise<void> {
        let HoldingRoomActive = await bot.database.getSetting(msg.guild, 'HoldingRoomActive');
        let HoldingRoomRoleID = await bot.database.getSetting(msg.guild, 'HoldingRoomRoleID');

        if (!enable) {
            if (HoldingRoomActive === 'false') {
                await bot.failReact(msg);
                await msg.channel.send('Holding room is already disabled silly.');
            } else {
                await bot.successReact(msg);
                bot.log.info(`Disabled holding room for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
                await bot.database.updateSettings(msg.guild, 'HoldingRoomActive', 'false');
                return;
            }
        }

        if (HoldingRoomActive === 'true') {
            await bot.failReact(msg);
            await msg.channel.send('Holding room is already enabled silly.');
            return;
        }

        // We are only checking for role id because it is the only value that is null
        // at initialization of guild configs
        if (!HoldingRoomRoleID) {
            let prefix = await bot.database.getSetting(msg.guild, 'Prefix');
            await bot.failReact(msg);
            let output = '';
            // TODO(sam): make this prettier
            output += 'Couldn\'t enable holding room because role is missing in your config!\n';
            output += `Try ${prefix} settings holdingRoom set role <roleName>`;
            await msg.channel.send(output);
        } else {
            await bot.successReact(msg);
            await bot.database.updateSettings(msg.guild, 'HoldingRoomActive', 'true');
            bot.log.info(`Enabled holding room for guild: "${msg.guild}" with id: "${msg.guild.id}".`);
        }
    }

    private async handleHoldingRoomSet(bot: SafetyJim, msg: Discord.Message, args: string[]): Promise<void> {
        switch (args[0]) {
            case 'role':
                let roleName = args.slice(1).join(' ');
                if (!msg.guild.roles.find('name', roleName)) {
                    await bot.failReact(msg);
                    // tslint:disable-next-line:max-line-length
                    await msg.channel.send(`No role called \`${roleName}\` found. Remember, role names are case sensitive!`);
                    return;
                } else {
                    let id = msg.guild.roles.find('name', roleName).id;
                    await bot.successReact(msg);
                    bot.log.info(`Updated role for holding room in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                    await bot.database.updateSettings(msg.guild, 'HoldingRoomRoleID', id);
                }
                break;
            case 'minutes':
                let input = args[1];
                let minute = parseInt(input);
                if (!minute) {
                    await bot.failReact(msg);
                    await msg.channel.send('Invalid input in minutes field, no changes were made!');
                    return;
                } else {
                    await bot.successReact(msg);
                    // tslint:disable-next-line:max-line-length
                    bot.log.info(`Updated minutes for holding room in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                    await bot.database.updateSettings(msg.guild, 'HoldingRoomMinutes', '' + minute);
                }
                break;
            case 'channel':
                if (msg.mentions.channels.size === 0 || !Discord.MessageMentions.CHANNELS_PATTERN.test(args[1])) {
                    await bot.failReact(msg);
                    await msg.channel.send('Invalid channel input, no changes were made!');
                    return;
                }

                let channel = msg.mentions.channels.first();

                await bot.successReact(msg);
                bot.log.info(`Updated channel for holding room in guild "${msg.guild}" with id: "${msg.guild.id}".`);
                await bot.database.updateSettings(msg.guild, 'HoldingRoomChannelID', channel.id);
                break;
            case 'message':
                let message = args.slice(1).join(' ');
                if (!message) {
                    await bot.failReact(msg);
                    await msg.channel.send('No message argument entered, no changes were made!');
                }
                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'WelcomeMessage', message);
                break;
        }
    }
}

export = Settings;
