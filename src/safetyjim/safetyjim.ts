import { Config } from '../config/config';
import * as winston from 'winston';
import * as Discord from 'discord.js';
import * as cron from 'cron';
import * as snekfetch from 'snekfetch';
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase, possibleKeys } from '../database/database';
const Package = require('../../package.json');
// tslint:disable-next-line:max-line-length
const DiscordBotsGuildID = '110373943822540800';
const DiscordBotListGuildID = '264445053596991498';

type RegexRecords = { string: RegExp };
type Commands = { string: Command };

export interface Command {
    usage: string | string[];
    run: (bot: SafetyJim, msg: Discord.Message, args: string) => Promise<boolean>;
}

export class SafetyJim {
    public client: Discord.Client;
    public bootTime: Date;
    private commandRegex = {} as RegexRecords;
    private prefixTestRegex = {} as RegexRecords;
    private commands = {} as Commands;
    private allowUsersCronJob;
    private unbanUserCronJob;
    private unmuteUserCronJob;

    constructor(public config: Config,
                public database: BotDatabase,
                public log: winston.LoggerInstance) {
        this.bootTime = new Date();
        this.loadCommands();
        log.info('Populating prefix regex dictionary.');
        this.database.getValuesOfKey('Prefix').then((prefixList) => {
            if (prefixList != null) {
                prefixList.forEach((prefix, id) => {
                    this.createRegexForGuild(id, prefix);
                });
            }
        });

        this.client = new Discord.Client({
            disableEveryone: true,
            disabledEvents: [
                'TYPING_START',
                'MESSAGE_UPDATE',
                'MESSAGE_REACTION_ADD',
                'MESSAGE_REACTION_REMOVE',
                'MESSAGE_REACTION_REMOVE_ALL',
                'USER_NOTE_UPDATE',
                'VOICE_SERVER_UPDATE',
                'RELATIONSHIP_ADD',
                'RELATIONSHIP_REMOVE',
            ],
        });
        this.client.on('ready', this.onReady());
        this.client.on('message', this.onMessage());
        this.client.on('guildCreate', this.onGuildCreate());
        this.client.on('guildDelete', this.onGuildDelete());
        this.client.on('guildMemberAdd', this.onGuildMemberAdd());
        this.client.on('guildMemberRemove', this.onGuildMemberRemove());

        this.client.login(config.discordToken);
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

    public updateDiscordBotLists(): void {
        if (this.config.discordbotspwToken) {
            snekfetch
                .post(`https://bots.discord.pw/api/bots/${this.client.user.id}/stats`)
                .set('Authorization', this.config.discordbotspwToken)
                .send({ server_count: this.client.guilds.size })
                .catch((err) => {
                    if (!err.stack.includes('504')) {
                        this.log.error(`Could not update pw with error ${err.stack}`);
                    }
                });
        }

        if (this.config.discordbotsToken) {
            snekfetch
                .post(`https://discordbots.org/api/bots/${this.client.user.id}/stats`)
                .set('Authorization', this.config.discordbotsToken)
                .send({ server_count: this.client.guilds.size })
                .catch((err) => { this.log.error(`Could not update discordbots with error ${err.stack}`); });
        }
    }

    private onReady(): () => void {
        return (() => {
            this.log.info(`Client is ready, username: ${this.client.user.username}.`);
            this.client.generateInvite([
                'KICK_MEMBERS',
                'BAN_MEMBERS',
                'ADD_REACTIONS',
                'READ_MESSAGES',
                'SEND_MESSAGES',
                'MANAGE_MESSAGES',
                'MANAGE_ROLES',
            ]).then((link) => this.log.info(`Bot invite link: ${link}`));

            this.client.guilds.filter((guild) => this.isBotFarm(guild)).map((guild) => guild.leave());

            this.populateGuildConfigDatabase();
            this.updateDiscordBotLists();
            this.client.user.setGame(`-mod help | ${Package.version}`);

            this.allowUsersCronJob = new cron.CronJob({ cronTime: '*/10 * * * * *',
                                                    onTick: this.allowUsers.bind(this), start: true, context: this });
            this.unbanUserCronJob = new cron.CronJob({ cronTime: '*/20 * * * * *',
                                                    onTick: this.unbanUsers.bind(this), start: true, context: this });
            this.unmuteUserCronJob = new cron.CronJob({ cronTime: '*/20 * * * * *',
                                                    onTick: this.unmuteUsers.bind(this), start: true, context: this });
        });
    }

    private onMessage(): (msg: Discord.Message) => void {
        return ((msg: Discord.Message) => {
            if (msg.author.bot || msg.channel.type === 'dm') {
                return;
            }

            if (msg.isMentioned(this.client.user)) {
                if (msg.content.includes('help') ||
                    msg.content.includes('command')) {
                    this.database.getSetting(msg.guild, 'Prefix')
                        .then((prefix) => {
                            this.successReact(msg);
                            msg.author.send({ embed: {
                                author: { name: 'Safety Jim - Commands', icon_url: this.client.user.avatarURL },
                                description: this.getUsageStrings(prefix),
                                color: 0x4286f4,
                            }});
                        });
                    return;
                } else if (msg.content.includes('prefix')) {
                    this.database.getSetting(msg.guild, 'Prefix')
                        .then((prefix) => {
                            this.successReact(msg);
                            msg.author.send({ embed: {
                                author: { name: 'Safety Jim - Prefix', icon_url: this.client.user.avatarURL },
                                description: `"${msg.guild.name}"s prefix is: ${prefix}`,
                                color: 0x4286f4,
                            }});
                        });
                    return;
                }
            }

            let testRegex: RegExp = this.prefixTestRegex[msg.guild.id];
            let cmdRegex: RegExp = this.commandRegex[msg.guild.id];

            let cmdMatch = msg.content.match(cmdRegex);
            // Check if user called bot without command or command was not found
            if (!cmdMatch || !Object.keys(this.commands).includes(cmdMatch[1])) {
                if (msg.cleanContent.match(testRegex)) {
                    this.failReact(msg);
                }
                return;
            }

            if (!msg.member.hasPermission('BAN_MEMBERS')) {
                this.failReact(msg);
                msg.channel.send('You need to have enough permissions to use this bot!');
                return;
            }

            this.executeCommand(msg, cmdMatch);
        }).bind(this);
    }

    private onGuildCreate(): (guild: Discord.Guild) => void {
        return ((guild: Discord.Guild) => {
            if (this.isBotFarm(guild)) {
                guild.leave();
                return;
            }

            guild.defaultChannel.send(`Hello! I am Safety Jim, \`${this.config.defaultPrefix}\` is my default prefix!`)
                                // tslint:disable-next-line:max-line-length
                                .catch(() => { guild.owner.send(`Hello! I am Safety Jim, \`${this.config.defaultPrefix}\` is my default prefix!`); });
            this.database.createGuildSettings(guild);
            this.createRegexForGuild(guild.id, this.config.defaultPrefix);
            this.updateDiscordBotLists();
            this.log.info(`Joined guild ${guild.name}`);
        });
    }

    private onGuildMemberAdd(): (member: Discord.GuildMember) => void {
        return (async (member: Discord.GuildMember) => {
            this.log.info(`${member.user.tag} joined guild ${member.guild.name}.`);
            let HoldingRoomActive = await this.database.getSetting(member.guild, 'HoldingRoomActive');

            if (HoldingRoomActive === 'true') {
                let HoldingRoomChannelID = await this.database.getSetting(member.guild, 'HoldingRoomChannelID');
                // tslint:disable-next-line:max-line-length
                let guildMinutes: string | number = await this.database.getSetting(member.guild, 'HoldingRoomMinutes');
                guildMinutes = parseInt(guildMinutes);
                if (this.client.channels.has(HoldingRoomChannelID)) {
                    let channel = this.client.channels.get(HoldingRoomChannelID) as Discord.TextChannel;
                    let message = await this.database.getSetting(member.guild, 'WelcomeMessage');

                    message = message.replace('$minute', guildMinutes + (guildMinutes === 1 ? ' minute' : ' minutes'))
                                     .replace('$user', member.user.toString())
                                     .replace('$guild', member.guild.name);
                    // tslint:disable-next-line:max-line-length
                    channel.send(message)
                           .catch((err) => { this.log.error(`There was an error when trying to send welcome message in ${member.guild.name}: ${err.toString()}`); });
                } else {
                    this.log.warn(`Could not find holding room channel for ${member.guild.name} : ${member.guild.id}`);
                    member.guild.defaultChannel.send('WARNING: Invalid channel is set as a holding room!');
                }

                this.database.createJoinRecord(member.user, member.guild, guildMinutes);
            }
        });
    }

    private onGuildMemberRemove(): (member: Discord.GuildMember) => void {
        return ((member: Discord.GuildMember) => {
            this.database.delJoinEntry(member.user.id, member.guild.id);
        });
    }

    private onGuildDelete(): (guild: Discord.Guild) => void {
        return ((guild: Discord.Guild) => {
            this.database.delGuildSettings(guild);
            delete this.commandRegex[guild.id];
            delete this.prefixTestRegex[guild.id];
            this.updateDiscordBotLists();
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

        try {
            showUsage = await this.commands[command].run(this, msg, args);
        } catch (e) {
            await this.failReact(msg);
            await msg.channel.send('There was an error running your command, this incident has been logged.');
            // tslint:disable-next-line:max-line-length
            this.log.error(`${command} failed with arguments: ${args} in guild "${msg.guild.name}" : ${e.stack + e.lineNumber + e.message}`);
        }

        if (showUsage === true) {
            let usage = this.commands[command].usage;
            let prefix = await this.database.getSetting(msg.guild, 'Prefix');

            await this.failReact(msg);
            await msg.channel.send({ embed: {
                author: {
                    name: `Safety Jim - "${command}" Syntax`,
                    icon_url: this.client.user.avatarURL,
                },
                description: this.getUsageString(prefix, usage),
                color: 0x4286f4,
                } });
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
                    this.log.warn(`Could not load command "${command}"!`);
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
        let usersToBeAllowed = await this.database.getUsersThatCanBeAllowed();

        for (let user of usersToBeAllowed) {
            let dGuild = this.client.guilds.get(user.GuildID);
            let enabled = await this.database.getSetting(dGuild, 'HoldingRoomActive');

            if (enabled === 'true') {
                await this.client.fetchUser(user.UserID);
                let dUser = await dGuild.fetchMember(user.UserID);
                let roleID = await this.database.getSetting(dGuild, 'HoldingRoomRoleID');

                try {
                    await dUser.addRole(roleID);
                    this.log.info(`Allowed "${dUser.user.tag}" in guild "${dGuild.name}".`);
                } finally {
                    await this.database.updateJoinRecord(user);
                }
            }
        }
    }

    private async unbanUsers(): Promise<void> {
        let usersToBeUnbanned = await this.database.getExpiredBans();

        if (usersToBeUnbanned == null) {
            return;
        }

        for (let user of usersToBeUnbanned) {
            let g = this.client.guilds.get(user.GuildID);

            if (g == null) {
                this.database.updateBanRecord(user);
            } else {
                try {
                    await g.unban(user.BannedUserID);
                    await this.database.updateBanRecord(user);
                    this.log.info(`Unbanned "${user.BannedUserName}" in guild "${g.name}".`);
                } catch (e) {
                    await this.database.updateBanRecord(user);
                    // tslint:disable-next-line:max-line-length
                    this.log.warn(`Could not unban user ${user.BannedUserName} (${user.BannedUserID}) in guild ${this.client.guilds.get(user.GuildID).id} : ${JSON.stringify(e)}`);
                }
            }
        }
    }

    private async unmuteUsers(): Promise<void> {
        let usersToBeUnmuted = await this.database.getExpiredMutes();

        for (let user of usersToBeUnmuted) {
            let guild = this.client.guilds.get(user.GuildID);

            if (!guild || !guild.roles.find('name', 'Muted')) {
                this.database.updateMuteRecord(user);
                return;
            }

            await this.client.fetchUser(user.MutedUserID);
            let member;

            try {
                member = await guild.fetchMember(user.MutedUserID);
            } catch (e) {
                this.database.updateMuteRecord(user);
            }

            if (!member) {
                this.database.updateMuteRecord(user);
                return;
            }
            try {
                await member.removeRole(guild.roles.find('name', 'Muted'));
            } catch (e) {
                this.database.updateMuteRecord(user);
            }
        }
    }

    private async populateGuildConfigDatabase(): Promise<void> {
        let guildsNotInDatabaseCount = 0;

        let configs = await this.database.getValuesOfKey('Prefix');
        let existingGuildIds = Array.from(configs.keys());

        this.client.guilds.map((guild) => {
            if (!existingGuildIds.includes(guild.id)) {
                this.database.createGuildSettings(guild);
                guildsNotInDatabaseCount++;
            }
        });

        if (guildsNotInDatabaseCount) {
            this.log.info(`Added ${guildsNotInDatabaseCount} guild(s) to database with default config.`);
        }

        let guildsWithMissingKeys = 0;

        for (let [guildID, guild] of this.client.guilds) {
            let settings = await this.database.getGuildSettings(guild);

            if (settings.size !== possibleKeys.length) {
                await this.database.delGuildSettings(guild);
                await this.database.createGuildSettings(guild);
                guildsWithMissingKeys++;
            }
        }

        if (guildsWithMissingKeys) {
            // tslint:disable-next-line:max-line-length
            this.log.info(`Resetted ${guildsWithMissingKeys} guild(s) to database with default config because of missing or extra keys.`);
        }
    }
}
