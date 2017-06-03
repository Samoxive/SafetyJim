import {Config} from '../config/config';
import * as winston from 'winston';
import * as Discord from 'discord.js';
import * as cron from 'cron';
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase } from '../database/database';

type RegexRecords = {string: RegExp};
type Commands = {string: Command};

export interface Command {
    usage: string | string[];
    run: (bot: SafetyJim, msg: Discord.Message, args: string) => boolean;
}

export class SafetyJim {
    public client: Discord.Client;
    private commandRegex = {} as RegexRecords;
    private prefixTestRegex = {} as RegexRecords;
    private commands = {} as Commands;
    private allowUsersCronJob;

    constructor(private config: Config,
                public database: BotDatabase,
                public log: winston.LoggerInstance) {
        this.loadCommands();
        log.info('Populating prefix regex dictionary.');
        this.database.getGuildPrefixes().then((prefixList) => {
            if (prefixList != null) {
                prefixList.map((record) => {
                    this.createRegexForGuild(record.GuildID, record.Prefix);
                });
            }
        });

        this.client = new Discord.Client();
        this.client.on('ready', this.onReady());
        this.client.on('message', this.onMessage());
        this.client.on('guildCreate', this.guildCreate());
        this.client.on('guildDelete', this.guildDelete());
        this.client.on('guildMemberAdd', this.guildMemberAdd());
        this.client.on('guildMemberRemove', this.guildMemberRemove());

        this.client.login(config.discordToken);
    }

    private onReady(): () => void {
        return (() => {
            this.log.info(`Client is ready, username: ${this.client.user.username}.`);
            this.client.generateInvite([]).then((link) => this.log.info(`Bot invite link: ${link}`));

            this.populateGuildConfigDatabase();
            this.populatePrefixDatabase();

            this.allowUsersCronJob = new cron.CronJob({cronTime: '*/30 * * * * *',
                                                       onTick: this.allowUsers.bind(this), start: true, context: this});
        });
    }

    private onMessage(): (msg: Discord.Message) => void {
        return ((msg: Discord.Message) => {
            if (msg.author.bot || msg.channel.type === 'dm') {
                return;
            }

            let testRegex: RegExp = this.prefixTestRegex[msg.guild.id];
            let cmdRegex: RegExp = this.commandRegex[msg.guild.id];

            let cmdMatch = msg.cleanContent.match(cmdRegex);
            // Check if user called bot without command or command was not found
            if (!cmdMatch || !Object.keys(this.commands).includes(cmdMatch[1])) {
                if (msg.cleanContent.match(testRegex)) {
                    if (!msg.member.hasPermission('BAN_MEMBERS')) {
                        msg.author.send('You don\'t have enough permissions to use this bot!');
                    } else {
                        this.database.getGuildPrefix(msg.guild)
                            .then((prefix) => {
                                let output = '';

                                for (let cmdString of Object.keys(this.commands)) {
                                   output += this.getUsageString(prefix, this.commands[cmdString].usage) + '\n';
                                }

                                return output.trim();
                            })
                            .then((s) => msg.channel.send(s, { code: '' }));
                    }
                }
                return;
            }

            let command = cmdMatch[1];
            let args = cmdMatch[2].trim();
            let showUsage;

            try {
                showUsage = this.commands[command].run(this, msg, args);
            } catch (e) {
                msg.channel.send('There was an error running the command:\n' +
                                '```\n' + e.toString() + '\n```');
                this.log.error(`${command} failed with arguments: ${args}`);
            }

            if (showUsage === true) {
                let usage = this.commands[command].usage;
                this.database.getGuildPrefix(msg.guild)
                             .then((prefix) => {
                                 msg.channel.send(this.getUsageString(prefix, usage), {code: ''});
                             });
            }
        }).bind(this);
    }

    private guildCreate(): (guild: Discord.Guild) => void {
        return ((guild: Discord.Guild) => {
            guild.defaultChannel.send(`Hello! I am Safety Jim, \`${this.config.defaultPrefix}\` is my default prefix!`);
            this.database.createGuildPrefix(guild, this.config.defaultPrefix);
            this.createRegexForGuild(guild.id, this.config.defaultPrefix);
            this.log.info(`Joined guild ${guild.name}`);
        });
    }

    private guildMemberAdd(): (member: Discord.GuildMember) => void {
        return (async (member: Discord.GuildMember) => {
            this.log.info(`${this.getUserDiscriminator(member.user)} joined guild ${member.guild.name}.`);
            let guildConfig = await this.database.getGuildConfiguration(member.guild);

            if (guildConfig.HoldingRoomActive) {
                if (this.client.channels.has(guildConfig.HoldingRoomChannelID)) {
                    let channel = this.client.channels.get(guildConfig.HoldingRoomChannelID) as Discord.TextChannel;
                    // tslint:disable-next-line:max-line-length
                    channel.send(`Welcome to ${member.guild.name} ${member.toString()}. You are in our holding room for ${guildConfig.HoldingRoomMinutes} minutes, please take this time to review our rules.`)
                           .catch((err) => { this.log.error(`There was an error when trying to send welcome message in ${member.guild.name}: ${err.toString()}`); });
                } else {
                    this.log.warn(`Could not find holding room channel for ${member.guild.name} : ${member.guild.id}`);
                    member.guild.defaultChannel.send('WARNING: Invalid channel is set as a holding room!');
                }

                this.database.createJoinRecord(member.user, member.guild, guildConfig.HoldingRoomMinutes);
            }
        });
    }

    private guildMemberRemove(): (member: Discord.GuildMember) => void {
        return ((member: Discord.GuildMember) => {
            this.database.delJoinEntry(member.user.id, member.guild.id);
        });
    }

    private guildDelete(): (guild: Discord.Guild) => void {
        return ((guild: Discord.Guild) => {
            this.database.delGuildSettings(guild);
        });
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

    private async allowUsers(): Promise<void> {
        let usersToBeAllowed = await this.database.getUsersThatCanBeAllowed();

        for (let user of usersToBeAllowed) {
            let guildConfig = await this.database.getGuildConfiguration(this.client.guilds.get(user.GuildID));

            if (guildConfig.HoldingRoomActive === 1) {
                let dGuild = this.client.guilds.get(user.GuildID);
                let dUser = dGuild.members.get(user.UserID);
                dUser.addRole(guildConfig.HoldingRoomRoleID);
                this.database.updateJoinRecord(user);
                this.log.info(`Allowed ${this.getUserDiscriminator(dUser.user)} in guild ${dGuild.name}.`);
            }
        }
    }

    private createRegexForGuild(guildID: string, prefix: string) {
        this.commandRegex[guildID] = new RegExp(`^${prefix}\\s+([^\\s]+)\\s*([^]*)\\s*`, 'i');
        this.prefixTestRegex[guildID] = new RegExp(`^${prefix}[\\s]*( .*)?$`, 'i');
    }

    private getUserDiscriminator(user: Discord.User) {
        return user.username + '#' + user.discriminator;
    }

    private getUsageString(prefix: string, usage: string | string[]): string {
        if (typeof usage === 'string') {
            return prefix + ' ' + usage;
        }

        return usage.map((u) => prefix + ' ' + u).join('\n');
    }

    private populateGuildConfigDatabase(): void {
        let guildsNotInDatabaseCount = 0;

        this.database.getGuildConfigurations()
                     .then((configs) => configs.map((config) => config.GuildID))
                     .then((existingGuildIds) => {
                        this.client.guilds.map((guild) => {
                            if (!existingGuildIds.includes(guild.id)) {
                                this.database.createGuildSettings(guild);
                                guildsNotInDatabaseCount++;
                            }
                        });
                     })
                     .then((_) => {
                         if (guildsNotInDatabaseCount) {
                             // tslint:disable-next-line:max-line-length
                             this.log.info(`Added ${guildsNotInDatabaseCount} guild(s) to database with default config.`);
                         }
                     });
    }

    private populatePrefixDatabase(): void {
        let existingRegexList = Object.keys(this.commandRegex);
        let guildsNotInDatabaseCount = 0;

        this.client.guilds.map((guild) => {
            if (!existingRegexList.includes(guild.id)) {
                this.createRegexForGuild(guild.id, this.config.defaultPrefix);
                this.database.createGuildPrefix(guild, this.config.defaultPrefix);
                guildsNotInDatabaseCount++;
            }
        });

        if (guildsNotInDatabaseCount) {
            this.log.info(`Added ${guildsNotInDatabaseCount} guild(s) to database with default prefix.`);
        }
    }
}
