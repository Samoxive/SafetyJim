import { SafetyJim, Command, MessageProcessor } from './safetyjim';
import { Config } from '../config/config';
import { BotDatabase, possibleKeys } from '../database/database';
import { Metrics } from '../metrics/metrics';
import * as Discord from 'discord.js';
import * as winston from 'winston';
import * as fs from 'fs';
import * as path from 'path';
import { Joins } from '../database/models/Joins';
import { Bans } from '../database/models/Bans';
import { Settings } from '../database/models/Settings';
import { Mutes } from '../database/models/Mutes';
import { CommandLogs } from '../database/models/CommandLogs';
import { Reminders } from '../database/models/Reminders';
import * as Utils from './utils';

type RegexRecords = { string: RegExp };
type Commands = { string: Command };
type Prefix = {
    guildid: string;
    prefix: string;
};

const DiscordBotsGuildID = '110373943822540800';
const DiscordBotListGuildID = '264445053596991498';
const NovoGuildID = '297462937646530562';

export class Shard {
    public client: Discord.Client;
    private commandRegex = {} as RegexRecords;
    private prefixTestRegex = {} as RegexRecords;
    private commands = {} as Commands;
    private processors = [] as MessageProcessor[];
    private unprocessedMessages: Discord.Message[] = [];

    constructor(private shardId: number,
                public jim: SafetyJim,
                public database: BotDatabase,
                public config: Config,
                public log: winston.LoggerInstance,
                private metrics: Metrics,
                prefixList: Prefix[]) {
        prefixList.forEach((setting) => {
            if (this.shardId === Utils.findShardIdFromGuildId(setting.guildid, this.config.jim.shard_count)) {
                this.createRegexForGuild(setting.guildid, setting.prefix);
            }
        });

        this.client = new Discord.Client({
            disableEveryone: true,
            disabledEvents: [
                'TYPING_START',
                'MESSAGE_UPDATE',
                'USER_NOTE_UPDATE',
                'VOICE_SERVER_UPDATE',
                'RELATIONSHIP_ADD',
                'RELATIONSHIP_REMOVE',
            ],
            shardId: this.shardId,
            shardCount: this.config.jim.shard_count,
        });

        this.client.on('ready', this.onReady());
        this.client.on('message', this.onMessage());
        this.client.on('messageDelete', this.onMessageDelete());
        this.client.on('guildCreate', this.onGuildCreate());
        this.client.on('guildDelete', this.onGuildDelete());
        this.client.on('guildMemberAdd', this.onGuildMemberAdd());
        this.client.on('guildMemberRemove', this.onGuildMemberRemove());
        this.client.on('messageReactionAdd', this.onReaction());
        this.client.on('messageReactionRemove', this.onReactionDelete());

        this.client.login(config.jim.token);
    }

    public createRegexForGuild(guildID: string, prefix: string) {
        prefix = prefix.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
        this.commandRegex[guildID] = new RegExp(`^${prefix}\\s+([^\\s]+)\\s*([^]*)\\s*`, 'i');
        this.prefixTestRegex[guildID] = new RegExp(`^${prefix}[\\s]*( .*)?$`, 'i');
    }

    public getUsageString(prefix: string, usage: string | string[]): string {
        if (typeof usage === 'string') {
            usage = usage.split(' - ');
            return `\`${prefix} ${usage[0]}\` - ${usage[1]}`;
        }

        return usage.map((cmdUsage) => {
            let u = cmdUsage.split(' - ');
            return `\`${prefix} ${u[0]}\` - ${u[1]}`;
        }).join('\n');
    }

    public getUsageStrings(prefix: string): string {
        return Object.keys(this.commands)
              .map((u) => this.getUsageString(prefix, this.commands[u].usage))
              .join('\n');
    }

    public async sendMessage(channel: Discord.Channel, message: string | Discord.MessageOptions): Promise<void> {
        let textChannel = channel as Discord.TextChannel;
        try {
            await textChannel.send(message);
        } catch (e) {
            this.log.warn(`Could not send a message in guild "${textChannel.guild.name}"`);
        }
    }

    public async createModLogEntry(msg: Discord.Message, member: Discord.GuildMember,
                                   reason: string, action: string, id: number, parsedTime?: number): Promise<void> {
        let colors = {
            ban: 0xFF2900,
            kick: 0xFF9900,
            warn: 0xFFEB00,
            mute: 0xFFFFFF,
            softban: 0xFF55DD,
        };

        let actionText = {
            ban: 'Ban',
            softban: 'Softban',
            kick: 'Kick',
            warn: 'Warn',
            mute: 'Mute',
        };

        let ModLogActive = await this.database.getGuildSetting(msg.guild, 'modlogactive');
        let prefix = await this.database.getGuildSetting(msg.guild, 'prefix');

        if (!ModLogActive || ModLogActive === 'false') {
            return;
        }

        let ModLogChannelID = await this.database.getGuildSetting(msg.guild, 'modlogchannelid');

        if (!this.client.channels.has(ModLogChannelID) ||
        this.client.channels.get(ModLogChannelID).type !== 'text') {
            // tslint:disable-next-line:max-line-length
            await this.sendMessage(msg.channel, `Invalid mod log channel in guild configuration, set a proper one via \`${prefix} settings\` command.`);
            return;
        }

        let logChannel = this.client.channels.get(ModLogChannelID) as Discord.TextChannel;

        let expires = parsedTime != null;

        let embed = {
        color: colors[action],
        fields: [
            { name: 'Action:', value: `${actionText[action]} - #${id}`, inline: false },
            { name: 'User:', value: `${member.user.tag} (${member.id})`, inline: false },
            { name: 'Reason:', value: reason, inline: false },
            { name: 'Responsible Moderator:', value: `${msg.author.tag} (${msg.author.id})`, inline: false },
            { name: 'Channel', value: msg.channel.toString(), inline: false },
            ],
            timestamp: new Date(),
        };

        if (expires) {
            let value = parsedTime ? new Date(parsedTime).toString() : 'Indefinitely';
            let untilText: string;

            switch (action) {
            case 'ban':
                untilText = 'Banned until';
                break;
            case 'mute':
                untilText = 'Muted until';
                break;
            default:
                break;
            }

            embed.fields.push({ name: untilText, value, inline: false });
        }

        try {
            await this.sendMessage(logChannel, { embed });
        } catch (e) {
            await this.sendMessage(msg.channel, 'Could not create a mod log entry!');
        }

        return;
    }

    public async failReact(msg: Discord.Message): Promise<void> {
        try {
            await msg.react('322698553980092417');
        } catch (e) {
            this.log.warn(`Could not react with fail emoji in guild "${msg.guild.name}"`);
        }

        return;
    }

    public async successReact(msg: Discord.Message): Promise<void> {
        try {
            await msg.react('322698554294534144');
        } catch (e) {
            this.log.warn(`Could not react with success emoji in guild "${msg.guild.name}"`);
        }

        return;
    }

    public async deleteCommandMessage(msg: Discord.Message): Promise<void> {
        let silentcommands = await this.database.getGuildSetting(msg.guild, 'silentcommands');

        if (silentcommands === 'false') {
            return;
        }

        try {
            await msg.delete();
        } catch (e) {
            //
        }
    }

    public loadCommand(cmd: Command, command: string): void {
        this.commands[command] = cmd;
    }

    public loadProcessor(proc: MessageProcessor): void {
        this.processors.push(proc);
    }

    private onReady(): () => void {
        return (async () => {
            this.log.info(`Client is ready, username: ${this.client.user.username}.`);

            let link = await this.client.generateInvite([
                'KICK_MEMBERS',
                'BAN_MEMBERS',
                'ADD_REACTIONS',
                'READ_MESSAGES',
                'SEND_MESSAGES',
                'MANAGE_MESSAGES',
                'MANAGE_ROLES',
            ]);

            this.log.info(`Bot invite link: ${link}`);

            for (let [id, guild] of this.client.guilds) {
                if (this.isBotFarm(guild)) {
                    await guild.leave();
                }
            }

            try {
                await this.populateGuildConfigDatabase();
            } catch (e) {
                this.log.error('something happened');
            }

            await this.client.user.setGame(`-mod help | ${this.config.version}`);

            for (let message of this.unprocessedMessages) {
                await this.onMessage()(message);
            }

            this.log.info('onReady finished.');
        });
    }

    private onMessage(): (msg: Discord.Message) => void {
        return (async (msg: Discord.Message) => {
            this.metrics.increment('client.message');
            if (msg.author.bot || msg.channel.type === 'dm') {
                return;
            }

            let testRegex: RegExp = this.prefixTestRegex[msg.guild.id];
            let cmdRegex: RegExp = this.commandRegex[msg.guild.id];

            if (!testRegex || !cmdRegex) {
                this.log.info('Added an unprocessed message: ' + msg.content);
                this.unprocessedMessages.push(msg);
                this.createRegexForGuild(msg.guild.id, this.config.jim.default_prefix);
                return;
            }

            if (msg.isMentioned(this.client.user) && msg.content.includes('prefix')) {
                let prefix = await this.database.getGuildSetting(msg.guild, 'prefix');

                await this.successReact(msg);

                let embed = {
                    author: { name: 'Safety Jim - Prefix', icon_url: this.client.user.avatarURL },
                    description: `This guild's prefix is: ${prefix}`,
                    color: 0x4286f4,
                };

                try {
                    await this.sendMessage(msg.channel, { embed });
                } catch (e) {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not send commands embed in guild: "${msg.guild}" requested by "${msg.author.tag}".`);
                }
                return;
            }

            for (let processor of this.processors) {
                let actionTaken;

                try {
                    actionTaken = await processor.onMessage(this, this.jim, msg);
                } catch (e) {
                    //
                }

                if (actionTaken === true) {
                    // The processor took a moderative action, halt further processing
                    return;
                }
            }

            let cmdMatch = msg.content.match(cmdRegex);
            // Check if user called bot without command or command was not found
            if (!cmdMatch || !Object.keys(this.commands).includes(cmdMatch[1])) {
                if (msg.cleanContent.match(testRegex)) {
                    await this.failReact(msg);
                }
                return;
            }

            await this.executeCommand(msg, cmdMatch);
        }).bind(this);
    }

    private onMessageDelete(): (msg: Discord.Message) => void {
        return (async (msg: Discord.Message) => {
            if (msg.author.bot || msg.channel.type === 'dm') {
                return;
            }

            for (let processor of this.processors) {
                if (processor.onMessageDelete) {
                    await processor.onMessageDelete(this, this.jim, msg);
                }
            }
        }).bind(this);
    }

    private onReaction(): (reaction: Discord.MessageReaction, user: Discord.User) => void {
        return (async (reaction: Discord.MessageReaction, user: Discord.User) => {
            if (reaction.me || reaction.message.channel.type === 'dm') {
                return;
            }

            for (let processor of this.processors) {
                if (processor.onReaction) {
                    await processor.onReaction(this, this.jim, reaction, user);
                }
            }
        }).bind(this);
    }

     private onReactionDelete(): (reaction: Discord.MessageReaction, user: Discord.User) => void {
        return (async (reaction: Discord.MessageReaction, user: Discord.User) => {
            if (reaction.me || reaction.message.channel.type === 'dm') {
                return;
            }

            for (let processor of this.processors) {
                if (processor.onReactionDelete) {
                    await processor.onReactionDelete(this, this.jim, reaction, user);
                }
            }
        }).bind(this);
    }

    private onGuildCreate(): (guild: Discord.Guild) => void {
        return (async (guild: Discord.Guild) => {
            if (this.isBotFarm(guild)) {
                try {
                    await guild.leave();
                } catch (e) {
                    this.log.error(`Could not leave guild ${guild.name} (${guild.id}) with error ${e}`);
                }
                return;
            }
            this.metrics.increment('guild.join');

            let message = `Hello! I am Safety Jim, \`${this.config.jim.default_prefix}\` is my default prefix!`;

            await this.sendMessage(Utils.getDefaultChannel(guild), message);

            await this.database.createGuildSettings(this, guild);
            this.createRegexForGuild(guild.id, this.config.jim.default_prefix);
            await this.jim.updateDiscordBotLists();
            this.log.info(`Joined guild ${guild.name}`);
        });
    }

    private onGuildMemberAdd(): (member: Discord.GuildMember) => void {
        return (async (member: Discord.GuildMember) => {
            let settings = await this.database.getGuildSettings(member.guild);

            if (settings.get('welcomemessageactive') === 'true') {
                if (this.client.channels.has(settings.get('welcomemessagechannelid'))) {
                    // tslint:disable-next-line:max-line-length
                    let channel = this.client.channels.get(settings.get('welcomemessagechannelid')) as Discord.TextChannel;
                    let message = settings.get('welcomemessage');

                    message = message.replace('$user', member.user.toString())
                                     .replace('$guild', member.guild.name);

                    if (settings.get('holdingroomactive') === 'true') {
                        let m = parseInt(settings.get('holdingroomminutes')) === 1 ? ' minute' : ' minutes';
                        message = message.replace('$minute', settings.get('holdingroomminutes') + m);
                    }
                    // tslint:disable-next-line:max-line-length
                    await this.sendMessage(channel, message);
                } else {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not find welcome message channel for ${member.guild.name} : ${member.guild.id}`);
                    await this.sendMessage(Utils.getDefaultChannel(member.guild), 'WARNING: Invalid channel is set for welcome messages!');
                }
            }

            if (settings.get('holdingroomactive') === 'true') {
                let guildMinutes: string | number = settings.get('holdingroomminutes');
                guildMinutes = parseInt(guildMinutes);

                let now = Math.round((new Date()).getTime() / 1000);
                await Joins.create<Joins>({
                    userid: member.user.id,
                    guildid: member.guild.id,
                    jointime: now,
                    allowtime: now + guildMinutes * 60,
                    allowed: false,
                });
            }
        });
    }

    private onGuildMemberRemove(): (member: Discord.GuildMember) => void {
        return (async (member: Discord.GuildMember) => {
            await Joins.destroy({
                where: {
                    userid: member.user.id,
                },
            });
        });
    }

    private onGuildDelete(): (guild: Discord.Guild) => void {
        return (async (guild: Discord.Guild) => {
            await Settings.destroy({
                where: {
                    guildid: guild.id,
                },
            });
            delete this.commandRegex[guild.id];
            delete this.prefixTestRegex[guild.id];
            await this.jim.updateDiscordBotLists();
            this.metrics.increment('guild.left');
        });
    }

    private onDisconnect(): (event: any) => void {
        return ((event: any) => {
            this.log.warn(`Client triggered disconnect event: ${JSON.stringify(event)}`);
        });
    }

    private async executeCommand(msg: Discord.Message, cmdMatch: RegExpMatchArray): Promise<void> {
        let command = cmdMatch[1];
        let args = cmdMatch[2].trim();
        let showUsage;
        let commandTime;

        CommandLogs.create<CommandLogs>({
            command,
            arguments: args,
            time: new Date(),
            username: msg.author.tag,
            userid: msg.author.id,
            guildname: msg.guild.name,
            guildid: msg.guild.id,
        });

        this.metrics.increment('command.count');
        try {
            commandTime = new Date();
            showUsage = await this.commands[command].run(this, this.jim, msg, args);
        } catch (e) {
            await this.failReact(msg);
            // tslint:disable-next-line:max-line-length
            await this.sendMessage(msg.channel, 'There was an error running your command, this incident has been logged.');
            // tslint:disable-next-line:max-line-length
            this.log.error(`${command} failed with arguments: ${args} in guild "${msg.guild.name}" : ${e.stack + e.lineNumber + e.message}`);
        } finally {
            this.metrics.increment(`${command}.count`);
            this.metrics.histogram(`${command}.time`, ((new Date()).getTime() - commandTime));
        }

        if (showUsage === true) {
            let usage = this.commands[command].usage;
            let prefix = await this.database.getGuildSetting(msg.guild, 'prefix');

            let embed = {
                author: {
                    name: `Safety Jim - "${command}" Syntax`,
                    icon_url: this.client.user.avatarURL,
                },
                description: this.getUsageString(prefix, usage),
                color: 0x4286f4,
            };

            await this.failReact(msg);
            await this.sendMessage(msg.channel, { embed });
        }
    }

    private isBotFarm(guild: Discord.Guild) {
        return (guild.id !== DiscordBotListGuildID) &&
               (guild.id !== DiscordBotsGuildID) &&
               (guild.id !== NovoGuildID) &&
               (guild.members.filter((member) => member.user.bot).size > 20);
    }

    private async populateGuildConfigDatabase(): Promise<void> {
        let guildsWithMissingKeys = 0;

        for (let [guildID, guild] of this.client.guilds) {
            let settings = await this.database.getGuildSettings(guild);

            if (settings.size !== possibleKeys.length) {
                await Settings.destroy({
                    where: {
                        guildid: guildID,
                    },
                });
                await this.database.createGuildSettings(this, guild);
                guildsWithMissingKeys++;
            }
        }

        if (guildsWithMissingKeys) {
            // tslint:disable-next-line:max-line-length
            this.log.info(`Resetted ${guildsWithMissingKeys} guild(s) to database with default config because of missing or extra keys.`);
        }

        let guildsNotInDatabaseCount = 0;

        let configs = await this.database.getValuesOfKey('prefix');
        let existingGuildIds = Array.from(configs.keys());

        for (let [id, guild] of this.client.guilds) {
            if (!existingGuildIds.includes(guild.id)) {
                await this.database.createGuildSettings(this, guild);
                guildsNotInDatabaseCount++;
            }
        }

        if (guildsNotInDatabaseCount) {
            this.log.info(`Added ${guildsNotInDatabaseCount} guild(s) to database with default config.`);
        }

        let prefixRecords = await this.database.getValuesOfKey('prefix');
        let existingCommandRegexes = Object.keys(this.commandRegex);
        let existingTestRegexes = Object.keys(this.prefixTestRegex);
        for (let [guildID, prefix] of prefixRecords) {
            if (!existingCommandRegexes.includes(guildID) || !existingCommandRegexes.includes(guildID)) {
                this.createRegexForGuild(guildID, prefix);
            }
        }
    }
}
