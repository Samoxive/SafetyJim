import { Config } from '../config/config';
import * as winston from 'winston';
import * as Discord from 'discord.js';
import * as cron from 'cron';
import * as snekfetch from 'snekfetch';
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase, possibleKeys } from '../database/database';
import { Joins } from '../database/models/Joins';
import { Bans } from '../database/models/Bans';
import { Settings } from '../database/models/Settings';
import { Mutes } from '../database/models/Mutes';
import { CommandLogs } from '../database/models/CommandLogs';
import { Metrics } from '../metrics/metrics';

const DiscordBotsGuildID = '110373943822540800';
const DiscordBotListGuildID = '264445053596991498';

type RegexRecords = { string: RegExp };
type Commands = { string: Command };

export interface Command {
    usage: string | string[];
    run: (bot: SafetyJim, msg: Discord.Message, args: string) => Promise<boolean>;
}

export interface MessageProcessor {
    onMessage?: (bot: SafetyJim, msg: Discord.Message) => Promise<boolean>;
    onMessageDelete?: (bot: SafetyJim, msg: Discord.Message) => Promise<void>;
    onReaction?: (bot: SafetyJim, reaction: Discord.MessageReaction, user: Discord.User) => Promise<void>;
    onReactionDelete?: (bot: SafetyJim, reaction: Discord.MessageReaction, user: Discord.User) => Promise<void>;
}

export class SafetyJim {
    public client: Discord.Client;
    public bootTime: Date;
    private commandRegex = {} as RegexRecords;
    private prefixTestRegex = {} as RegexRecords;
    private commands = {} as Commands;
    private processors = [] as MessageProcessor[];
    private allowUsersCronJob;
    private unbanUserCronJob;
    private unmuteUserCronJob;
    private metricsCronJob;
    private unprocessedMessages: Discord.Message[] = [];
    private metrics: Metrics;

    constructor(public config: Config,
                public database: BotDatabase,
                public log: winston.LoggerInstance) {
        this.bootTime = new Date();
        this.loadCommands();
        this.loadProcessors();
        log.info('Populating prefix regex dictionary.');
        Settings.findAll<Settings>({
            where: {
                key: 'prefix',
            },
        }).then((prefixList) => {
            prefixList.forEach((setting) => {
                this.createRegexForGuild(setting.guildid, setting.value);
            });
        });

        this.metrics = new Metrics(this.config, 'jim');
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

    public getDefaultChannel(guild: Discord.Guild): Discord.TextChannel {
        for (let [id, channel] of guild.channels) {
            if (channel.permissionsFor(guild.me).has('SEND_MESSAGES') && channel.type === 'text') {
                return channel as Discord.TextChannel;
            }
        }

        return guild.channels.first() as Discord.TextChannel;
    }

    public async sendMessage(channel: Discord.Channel, message: string | Discord.MessageOptions): Promise<void> {
        let textChannel = channel as Discord.TextChannel;
        try {
            await textChannel.send(message);
        } catch (e) {
            this.log.warn(`Could not send a message in guild "${textChannel.guild.name}"`);
        }
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

    public async updateDiscordBotLists(): Promise<void> {
        if (!this.config.botlist.enabled) {
            return;
        }

        for (let list of this.config.botlist.list) {
            try {
                // TODO(sam): replace snekfetch at some point, it is a less active
                // undocumented library
                await snekfetch
                        .post(list.url.replace('$id', this.client.user.id))
                        .set('Authorization', list.token)
                        .send({ server_count: this.client.guilds.size })
                        .then();
            } catch (err) {
                if (!list.ignore_errors) {
                    this.log.error(`Updating ${list.name} failed with error ${err}`);
                }
            }
        }
    }

    public async createModLogEntry(msg: Discord.Message, member: Discord.GuildMember,
                                   reason: string, action: string, id: number, parsedTime?: number): Promise<void> {
        let colors = {
            ban: 0xFF2900,
            kick: 0xFF9900,
            warn: 0xFFEB00,
            mute: 0xFFFFFF,
        };

        let actionText = {
            ban: 'Ban',
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

            this.allowUsersCronJob = new cron.CronJob({ cronTime: '*/10 * * * * *',
                                                    onTick: this.allowUsers.bind(this), start: true, context: this });
            this.unbanUserCronJob = new cron.CronJob({ cronTime: '*/20 * * * * *',
                                                    onTick: this.unbanUsers.bind(this), start: true, context: this });
            this.unmuteUserCronJob = new cron.CronJob({ cronTime: '*/20 * * * * *',
                                                    onTick: this.unmuteUsers.bind(this), start: true, context: this });
            this.metricsCronJob = new cron.CronJob({ cronTime: '*/20 * * * * *',
                                                onTick: this.updateMetrics.bind(this), start: true, context: this });

            this.metrics.gauge('guild.count', this.client.guilds.size);
            this.metrics.increment('client.ready');

            try {
                await this.populateGuildConfigDatabase();
            } catch (e) {
                this.log.error('something happened');
            }
            await this.updateDiscordBotLists();
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
                    actionTaken = await processor.onMessage(this, msg);
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
                    await processor.onMessageDelete(this, msg);
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
                    await processor.onReaction(this, reaction, user);
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
                    await processor.onReactionDelete(this, reaction, user);
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
            this.metrics.gauge('guild.count', this.client.guilds.size);

            let message = `Hello! I am Safety Jim, \`${this.config.jim.default_prefix}\` is my default prefix!`;

            try {
                this.sendMessage(this.getDefaultChannel(guild), message);
            } catch (e) {
                try {
                    // Could not send to default channel because of permissions
                    guild.owner.send(message);
                } catch (e) {
                    // Owner likely blocked messages from members, do nothing further
                }
            }

            await this.database.createGuildSettings(this, guild);
            this.createRegexForGuild(guild.id, this.config.jim.default_prefix);
            await this.updateDiscordBotLists();
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
                    this.sendMessage(channel, message);
                } else {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not find welcome message channel for ${member.guild.name} : ${member.guild.id}`);
                    this.sendMessage(this.getDefaultChannel(member.guild), 'WARNING: Invalid channel is set for welcome messages!');
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
            await this.updateDiscordBotLists();
            this.metrics.increment('guild.left');
            this.metrics.gauge('guild.count', this.client.guilds.size);
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
            showUsage = await this.commands[command].run(this, msg, args);
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

    private loadProcessors(): void {
        let processorsFolderPath = path.join(__dirname, '..', 'processors');
        if (!fs.existsSync(processorsFolderPath) || !fs.statSync(processorsFolderPath).isDirectory()) {
            this.log.error('Processors directory could not be found!');
            process.exit(1);
        }

        let processorList = fs.readdirSync(processorsFolderPath);

        for (let processor of processorList) {
            if (!fs.statSync(path.join(processorsFolderPath, processor)).isDirectory()) {
                this.log.warn(`Found file "${processor}", ignoring...`);
            } else {
                try {
                    let proc: MessageProcessor = require(path.join(processorsFolderPath, processor, processor + '.js'));
                    this.processors.push(new proc(this));
                    this.log.info(`Loaded processor "${processor}"`);
                } catch (e) {
                    this.log.warn(`Could not load processor "${processor}"!`);
                }
            }
        }
    }

    private loadCommands(): void {
        let commandsFolderPath = path.join(__dirname, '..', 'commands');
        if (!fs.existsSync(commandsFolderPath) || !fs.statSync(commandsFolderPath).isDirectory()) {
            this.log.error('Commands directory could not be found!');
            process.exit(1);
        }

        let commandList = fs.readdirSync(commandsFolderPath);

        for (let command of commandList) {
            if (!fs.statSync(path.join(commandsFolderPath, command)).isDirectory()) {
                this.log.warn(`Found file "${command}", ignoring...`);
            } else {
                try {
                    let cmd = require(path.join(commandsFolderPath, command, command + '.js')) as Command;
                    this.commands[command] = new cmd(this);
                    this.log.info(`Loaded command "${command}"`);
                } catch (e) {
                    this.log.warn(`Could not load command "${command}"! ${e.stack}`);
                }
            }
        }
    }

    private isBotFarm(guild: Discord.Guild) {
        return (guild.id !== DiscordBotListGuildID) &&
               (guild.id !== DiscordBotsGuildID) &&
               (guild.members.filter((member) => member.user.bot).size > 20);
    }

    private async allowUsers(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let usersToBeAllowed = await Joins.findAll<Joins>({
            where: {
                allowed: false,
                allowtime: {
                    $lt: now,
                },
            },
        });

        for (let user of usersToBeAllowed) {
            let dGuild = this.client.guilds.get(user.guildid);

            if (dGuild == null) {
                await Joins.update<Joins>({ allowed: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
                continue;
            }

            let enabled = await this.database.getGuildSetting(dGuild, 'holdingroomactive');

            if (enabled === 'true') {
                await this.client.fetchUser(user.userid, true);
                let dUser = await dGuild.fetchMember(user.userid);
                let roleID = await this.database.getGuildSetting(dGuild, 'holdingroomroleid');
                try {
                    await dUser.addRole(roleID);
                    this.log.info(`Allowed "${dUser.user.tag}" in guild "${dGuild.name}".`);
                } catch (e) {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not allow user ${dUser.user.tag} (${dUser.id}) in guild ${this.client.guilds.get(user.guildid).id} : ${JSON.stringify(e)}`);
                    await this.sendMessage(this.getDefaultChannel(dGuild), 'I could not allow a user into the server from the holding room. I probably don\'t have permissions!');
                } finally {
                    await Joins.update({ allowed: true }, {
                        where: {
                            userid: user.userid,
                            guildid: user.guildid,
                        },
                    });
                }
            }
        }
    }

    private async unbanUsers(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let usersToBeUnbanned = await Bans.findAll<Bans>({
            where: {
                unbanned: false,
                expires: true,
                expiretime: {
                    $lt: now,
                },
            },
        });

        if (usersToBeUnbanned == null) {
            return;
        }

        for (let user of usersToBeUnbanned) {
            let g = this.client.guilds.get(user.guildid);

            if (g == null) {
                Bans.update<Bans>({ unbanned: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
            } else {
                let unbanUser = await this.client.fetchUser(user.userid);
                try {
                    await g.unban(unbanUser);
                    this.log.info(`Unbanned "${unbanUser.tag}" in guild "${g.name}".`);
                } catch (e) {
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not unban user ${unbanUser.tag} (${unbanUser.id}) in guild ${this.client.guilds.get(user.guildid).id} : ${JSON.stringify(e)}`);
                    await this.sendMessage(this.getDefaultChannel(g), 'I could not unban a user that was previously temporarily banned. I probably don\'t have permissions!');
                } finally {
                    Bans.update({ unbanned: true }, {
                        where: {
                            userid: user.userid,
                            guildid: user.guildid,
                        },
                    });
                }
            }
        }
    }

    private async unmuteUsers(): Promise<void> {
        let now = Math.round((new Date()).getTime() / 1000);

        let usersToBeUnmuted = await Mutes.findAll<Mutes>({
            where: {
                unmuted: false,
                expires: true,
                expiretime: {
                    $lt: now,
                },
            },
        });

        for (let user of usersToBeUnmuted) {
            let guild = this.client.guilds.get(user.guildid);

            if (!guild || !guild.roles.find('name', 'Muted')) {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
                return;
            }

            await this.client.fetchUser(user.userid, true);
            let member: Discord.GuildMember;

            try {
                await this.client.fetchUser(user.userid);
                member = await guild.fetchMember(user.userid);
            } catch (e) {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
            }

            if (!member) {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
                return;
            }
            try {
                await member.removeRole(guild.roles.find('name', 'Muted'));
            } catch (e) {
                // tslint:disable-next-line:max-line-length
                this.log.warn(`Could not unmute user ${member.user.tag} (${member.id}) in guild ${this.client.guilds.get(user.userid).id} : ${JSON.stringify(e)}`);
                await this.sendMessage(this.getDefaultChannel(guild), 'I could not unban a user that was previously temporarily banned. I probably don\'t have permissions!');
            } finally {
                await Mutes.update({ unmuted: true }, {
                    where: {
                        userid: user.userid,
                        guildid: user.guildid,
                    },
                });
            }
        }
    }

    private updateMetrics(): void {
        this.metrics.gauge('guild.count', this.client.guilds.size);
        this.metrics.gauge('memoryUsage', process.memoryUsage().rss / (1024 * 1024));
        this.metrics.gauge('uptime', Math.floor(process.uptime() / (60 * 60)));
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
