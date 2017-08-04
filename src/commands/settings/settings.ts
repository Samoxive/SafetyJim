import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { possibleKeys, defaultWelcomeMessage, SettingKey } from '../../database/database';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';

const keys = ['modlog', 'modlogchannel', 'holdingroomrole', 'holdingroom',
    'holdingroomminutes', 'embedcolor', 'prefix', 'welcomemessage', 'message', 'welcomemessagechannel'];

class SettingsCommand implements Command {
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

        if (splitArgs[0] === 'list') {
            let output = '`EmbedColor <hexadecimal rgb>` - Default: 4286F4\n' +
                         '`HoldingRoom <enabled/disabled>` - Default: disabled\n' +
                         '`HoldingRoomMinutes <number>` - Default: 3\n' +
                         '`HoldingRoomRole <text>` - Default: None\n' +
                         '`ModLog <enabled/disabled>` - Default: disabled\n' +
                         `\`ModLogChannel <#channel>\` - Default: ${msg.guild.defaultChannel}\n` +
                         '`Prefix <text>` - Default: -mod\n' +
                         '\`WelcomeMessage <enabled/disabled>\` - Default: disabled\n' +
                         `\`WelcomeMessageChannel <#channel>\` - Default: ${msg.guild.defaultChannel}\n` +
                         `\`Message <text>\` - Default: ${defaultWelcomeMessage}`;
            let embed = {
                author: { name: 'Safety Jim', icon_url: bot.client.user.avatarURL },
                fields: [{ name: 'List of settings', value: output }],
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
            await Settings.destroy({
                where: {
                    guildid: msg.guild,
                },
            });
            await bot.database.createGuildSettings(msg.guild);
            bot.createRegexForGuild(msg.guild.id, bot.config.jim.default_prefix);
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

        switch (setKey) {
            case 'holdingroom':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                let roleID = (await Settings.find<Settings>({
                    where: {
                        guildid: msg.guild.id,
                        key: 'holdingroomroleid',
                    },
                })).value;

                if (roleID == null) {
                    await bot.failReact(msg);
                    await msg.channel.send('You can\'t enable holding room because you didn\'t set a role first!');
                    return;
                }
                await bot.successReact(msg);
                await Settings.update<Settings>({ value: setArgument }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'holdingroomactive',
                    },
                });
                break;
            case 'modlog':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                await bot.successReact(msg);
                await Settings.update<Settings>({ value: setArgument }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'modlogactive',
                    },
                });
                break;
            case 'welcomemessage':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                await bot.successReact(msg);
                await Settings.update<Settings>({ value: setArgument }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'welcomemessageactive',
                    },
                });
                break;
            case 'message':
                await bot.successReact(msg);
                await Settings.update<Settings>({ value: setArgument }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'welcomemessage',
                    },
                });
                break;
            case 'prefix':
                await bot.successReact(msg);
                await Settings.update<Settings>({ value: setArgument }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'prefix',
                    },
                });
                bot.createRegexForGuild(msg.guild.id, setArgument);
                break;
            case 'holdingroomminutes':
                let minutes = parseInt(setArguments[0]);

                if (isNaN(minutes)) {
                    return true;
                }

                await bot.successReact(msg);
                await Settings.update<Settings>({ value: minutes.toString() }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'holdingroomminutes',
                    },
                });
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
                await Settings.update<Settings>({ value: setArguments[0] }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'embedcolor',
                    },
                });
                break;
            case 'welcomemessagechannel':
            case 'modlogchannel':
                if (setArguments.length === 1 &&
                    !setArgument.match(Discord.MessageMentions.CHANNELS_PATTERN)) {
                    return true;
                }

                setKey = setKey === 'modlogchannel' ? 'modlogchannelid' : 'welcomemessagechannelid';

                await bot.successReact(msg);
                await Settings.update<Settings>({ value: msg.mentions.channels.first().id }, {
                    where: {
                        guildid: msg.guild.id,
                        key: setKey,
                    },
                });
                break;
            case 'holdingroomrole':
                let role = msg.guild.roles.find('name', setArgument);

                if (!role) {
                    return true;
                }

                await bot.successReact(msg);
                await Settings.update<Settings>({ value: role.id }, {
                    where: {
                        guildid: msg.guild.id,
                        key: 'holdingroomroleid',
                    },
                });
                break;
            default:
                await bot.failReact(msg);
                return true;
        }

        return;
    }

    private async getSettingsString(bot: SafetyJim, msg: Discord.Message): Promise<{ color: number, output: string}> {
        let config = await bot.database.getGuildSettings(msg.guild);
        let output = '';
        output += `**Prefix:** ${config.get('prefix')}\n`;
        output += `**Embed color:** #${config.get('embedcolor')}\n`;

        if (config.get('modlogactive') === 'false') {
            output += '**Mod Log:** Disabled\n';
        } else {
            output += '**Mod Log:** Enabled\n';
            output += `\t**Mod Log Channel:** ${msg.guild.channels.get(config.get('modlogchannelid'))}\n`;
        }

        if (config.get('welcomemessageactive') === 'false') {
            output += '**Welcome Messages:** Disabled\n';
        } else {
            output += '**Welcome Messages:** Enabled\n';
            // tslint:disable-next-line:max-line-length
            output += `\t**Welcome Message Channel:** ${msg.guild.channels.get(config.get('welcomemessagechannelid'))}\n`;
        }

        if (config.get('holdingroomactive') === 'false') {
            output += '**Holding Room:** Disabled\n';
        } else {
            output += '**Holding Room:** Enabled\n';
            output += `\t**Holding Room Role:** ${msg.guild.roles.get(config.get('holdingroomroleid')).name}\n`;
            output += `\t**Holding Room Delay:** ${config.get('holdingroomminutes')} minute(s)`;
        }

        return { output, color: parseInt(config.get('embedcolor'), 16) };
    }

    private async handleSettingsDisplay(bot: SafetyJim, msg: Discord.Message): Promise<void> {
        let output = await this.getSettingsString(bot, msg);

        let embed = {
            author: { name: 'Safety Jim', icon_url: bot.client.user.avatarURL },
            fields: [{ name: 'Guild Settings', value: output.output }],
            color: output.color,
        };
        await bot.successReact(msg);
        await msg.channel.send({ embed });
    }
}

export = SettingsCommand;
