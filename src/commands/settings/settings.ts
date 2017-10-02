import { Command, SafetyJim } from '../../safetyjim/safetyjim';
import { Shard } from '../../safetyjim/shard';
import * as Utils from '../../safetyjim/utils';
import { possibleKeys, defaultWelcomeMessage, SettingKey } from '../../database/database';
import * as Discord from 'discord.js';
import { Settings } from '../../database/models/Settings';

const keys = ['modlog',
              'modlogchannel',
              'holdingroomrole',
              'holdingroom',
              'holdingroomminutes',
              'prefix',
              'welcomemessage',
              'message',
              'welcomemessagechannel',
              'invitelinkremover',
              'silentcommands'];

class SettingsCommand implements Command {
    public usage = [
        'settings display - shows current state of settings',
        'settings list - lists the keys you can use to customize the bot',
        'settings reset - resets every setting to their default value',
        'settings set <key> <value> - changes given key\'s value',
    ];

    // tslint:disable-next-line:no-empty
    constructor(bot: SafetyJim) {}

    public async run(shard: Shard, jim: SafetyJim, msg: Discord.Message, args: string): Promise<boolean> {
        let splitArgs = args.split(' ');
        if (!args || !['display', 'list', 'reset', 'set'].includes(splitArgs[0])) {
            return true;
        }

        if (splitArgs[0] === 'display') {
            await Utils.successReact(jim, msg);
            await this.handleSettingsDisplay(shard, msg);
            return;
        }

        if (splitArgs[0] === 'list') {
            let output = '`HoldingRoom <enabled/disabled>` - Default: disabled\n' +
                         '`HoldingRoomMinutes <number>` - Default: 3\n' +
                         '`HoldingRoomRole <text>` - Default: None\n' +
                         '`ModLog <enabled/disabled>` - Default: disabled\n' +
                         `\`ModLogChannel <#channel>\` - Default: ${Utils.getDefaultChannel(msg.guild)}\n` +
                         '`Prefix <text>` - Default: -mod\n' +
                         '\`WelcomeMessage <enabled/disabled>\` - Default: disabled\n' +
                         `\`WelcomeMessageChannel <#channel>\` - Default: ${Utils.getDefaultChannel(msg.guild)}\n` +
                         `\`Message <text>\` - Default: ${defaultWelcomeMessage}\n` +
                         '`InviteLinkRemover <enabled/disabled>` - Default: disabled\n' +
                         '`SilentCommands <enabled/disabled>` - Default: disabled';
            let embed = {
                author: { name: 'Safety Jim', icon_url: shard.client.user.avatarURL },
                fields: [{ name: 'List of settings', value: output }],
                color: 0x4286f4,
            };
            await Utils.successReact(jim, msg);
            await Utils.sendMessage(msg.channel, { embed });
            return;
        }

        if (!msg.member.hasPermission('ADMINISTRATOR')) {
            await Utils.failReact(jim, msg);
            await Utils.sendMessage(msg.channel, 'You don\'t have enough permissions to modify guild settings!');
            return;
        }

        if (splitArgs[0] === 'reset') {
            await Settings.destroy({
                where: {
                    guildid: msg.guild.id,
                },
            });
            await jim.database.createGuildSettings(shard, msg.guild);
            shard.createRegexForGuild(msg.guild.id, jim.config.jim.default_prefix);
            await Utils.successReact(jim, msg);
            return;
        }

        let setKey = splitArgs[1].toLowerCase();
        let setArguments = splitArgs.slice(2);
        let setArgument = setArguments.join(' ');

        if (!keys.includes(setKey) || !setArgument) {
            await Utils.failReact(jim, msg);
            return true;
        }

        switch (setKey) {
            case 'silentcommands':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'silentcommands', setArgument);
                break;
            case 'invitelinkremover':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'invitelinkremover', setArgument);
                break;
            case 'holdingroom':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                let roleID = await jim.database.getGuildSetting(msg.guild, 'holdingroomroleid');

                if (roleID == null) {
                    await Utils.failReact(jim, msg);
                    // tslint:disable-next-line:max-line-length
                    await Utils.sendMessage(msg.channel, 'You can\'t enable holding room because you didn\'t set a role first!');
                    return;
                }
                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'holdingroomactive', setArgument);
                break;
            case 'modlog':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'modlogactive', setArgument);
                break;
            case 'welcomemessage':
                if (setArgument === 'enabled') {
                    setArgument = 'true';
                } else if (setArgument === 'disabled') {
                    setArgument = 'false';
                } else {
                    return true;
                }

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'welcomemessageactive', setArgument);
                break;
            case 'message':
                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'welcomemessage', setArgument);
                break;
            case 'prefix':
                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'prefix', setArgument);
                shard.createRegexForGuild(msg.guild.id, setArgument);
                break;
            case 'holdingroomminutes':
                let minutes = parseInt(setArguments[0]);

                if (isNaN(minutes)) {
                    return true;
                }

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'holdingroomminutes', minutes.toString());
                break;
            case 'welcomemessagechannel':
            case 'modlogchannel':
                if (setArguments.length === 1 &&
                    !setArgument.match(Discord.MessageMentions.CHANNELS_PATTERN)) {
                    return true;
                }

                let key: SettingKey = (setKey === 'modlogchannel' ? 'modlogchannelid' : 'welcomemessagechannelid');

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, key, msg.mentions.channels.first().id);
                break;
            case 'holdingroomrole':
                let role = msg.guild.roles.find('name', setArgument);

                if (!role) {
                    return true;
                }

                await Utils.successReact(jim, msg);
                await jim.database.updateSetting(msg.guild, 'holdingroomroleid', role.id);
                break;
            default:
                await Utils.failReact(jim, msg);
                return true;
        }

        await Utils.deleteCommandMessage(jim, msg);
        return;
    }

    private async getSettingsString(jim: SafetyJim, msg: Discord.Message): Promise<string> {
        let config = await jim.database.getGuildSettings(msg.guild);
        let output = '';
        output += `**Prefix:** ${config.get('prefix')}\n`;

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
            output += `\t**Holding Room Delay:** ${config.get('holdingroomminutes')} minute(s)\n`;
        }

        if (config.get('invitelinkremover') === 'true') {
            output += '**Invite Link Remover:** Enabled\n';
        } else {
            output += '**Invite Link Remover:** Disabled\n';
        }

        if (config.get('silentcommands') === 'true') {
            output += '**Silent Commands:** Enabled\n';
        } else {
            output += '**Silent Commands:** Disabled\n';
        }

        return output;
    }

    private async handleSettingsDisplay(shard: Shard, msg: Discord.Message): Promise<void> {
        let output = await this.getSettingsString(shard.jim, msg);

        let embed = {
            author: { name: 'Safety Jim', icon_url: shard.client.user.avatarURL },
            fields: [{ name: 'Guild Settings', value: output }],
            color: 0x4286f4,
        };
        await Utils.successReact(shard.jim, msg);
        await Utils.sendMessage(msg.channel, { embed });
    }
}

export = SettingsCommand;
