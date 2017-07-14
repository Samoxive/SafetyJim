import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { possibleKeys, defaultWelcomeMessage, SettingKey } from '../../database/database';
import * as Discord from 'discord.js';

const keys = ['modlog', 'modlogchannel', 'holdingroomrole', 'holdingroom',
    'holdingroomminutes', 'holdingroomchannel', 'embedcolor', 'prefix', 'welcomemessage'];

class Settings implements Command {
    public usage = [
        'settings display - shows current state of settings',
        'settings list - lists the keys you can use to customize the bot',
        'settings reset - resets every setting to their default value',
        'settings set <key> <value> - changes given key\'s value',
    ];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(bot: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        if (!args || !['display', 'list', 'reset', 'set'].includes(splitArgs[0])) {
            return true;
        }

        if (splitArgs[0] === 'display') {
            await bot.successReact(msg);
            await this.handleSettingsDisplay(bot, msg);
            return;
        }

        // TODO(sam): Change this into embed
        if (splitArgs[0] === 'list') {
            let output = '`EmbedColor <hexadecimal rgb>` - Default: 4286F4\n' +
                         '`HoldingRoom <enabled/disabled>` - Default: disabled\n' +
                         `\`HoldingRoomChannel <#channel>\` - Default: ${msg.guild.defaultChannel}\n` +
                         '`HoldingRoomMinutes <number>` - Default: 3\n' +
                         '`HoldingRoomRole <text>` - Default: None\n' +
                         '`ModLog <enabled/disabled>` - Default: disabled\n' +
                         `\`ModLogChannel <#channel>\` - Default: ${msg.guild.defaultChannel}\n` +
                         '`Prefix <text>` - Default: -mod\n' +
                         `\`WelcomeMessage <text>\` - Default: ${defaultWelcomeMessage}`;
            let embed = {
                author: { name: 'Safety Jim - Settings List', icon_url: bot.client.user.avatarURL },
                description: output,
                color: 0x4286f4,
            };
            await bot.successReact(msg);
            await msg.channel.send({ embed });
            return;
        }

        if (!msg.member.hasPermission('ADMINISTRATOR')) {
            await bot.failReact(msg);
            await msg.author.send('You don\'t have enough permissions to modify guild settings!');
            return;
        }

        if (splitArgs[0] === 'reset') {
            await bot.database.delGuildSettings(msg.guild);
            await bot.database.createGuildSettings(msg.guild);
            bot.createRegexForGuild(msg.guild.id, bot.config.defaultPrefix);
            await bot.successReact(msg);
            return;
        }

        let setKey = splitArgs[1].toLowerCase();
        let setArguments = splitArgs.slice(2);
        let setArgument = setArguments.join(' ');

        if (!keys.includes(setKey) || !setArgument) {
            await bot.failReact(msg);
            return true;
        }

        // TODO(sam): maybe make keys more user friendly?
        switch (setKey) {
            case 'modlog':
            case 'holdingroom':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                setKey = setKey === 'modlog' ? 'ModLogActive' : 'HoldingRoomActive';
                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, setKey, setArgument);
                break;
            case 'welcomemessage':
                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'WelcomeMessage', setArgument);
                break;
            case 'prefix':
                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'Prefix', setArgument);
                bot.createRegexForGuild(msg.guild.id, setArgument);
                break;
            case 'holdingroomminutes':
                let minutes = parseInt(setArguments[0]);

                if (isNaN(minutes)) {
                    return true;
                }

                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'HoldingRoomMinutes', minutes.toString());
                break;
            case 'embedcolor':
                if (setArguments[0].length !== 6) {
                    return true;
                }
                let color = parseInt(setArguments[0], 16);
                if (isNaN(color)) {
                    return true;
                }
                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'EmbedColor', color.toString());
                break;
            case 'holdingroomchannel':
            case 'modlogchannel':
                if (setArguments.length === 1 &&
                    !setArgument.match(Discord.MessageMentions.CHANNELS_PATTERN)) {
                    return true;
                }

                setKey = setKey === 'modlogchannel' ? 'ModLogChannelID' : 'HoldingRoomChannelID';

                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, setKey, msg.mentions.channels.first().id);
                break;
            case 'holdingroomrole':
                let role = msg.guild.roles.find('name', setArgument);

                if (!role) {
                    return true;
                }

                await bot.successReact(msg);
                await bot.database.updateSettings(msg.guild, 'HoldingRoomRoleID', role.id);
                break;
            default:
                await bot.failReact(msg);
                return true;
        }

        return;
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
}

export = Settings;
