import {Config} from '../config/config';
import * as winston from 'winston';
import * as Discord from 'discord.js';
import * as cron from 'cron';
import { BotDatabase } from '../database/database';

type RegexRecords = {string: RegExp};
type Commands = {string: Command};

interface Command {
    usage: string[] | string;
    run: (bot: SafetyJim, msg: Discord.Message, args: string) => boolean;
    init?: (bot: SafetyJim) => void;
}

export class SafetyJim {
    private client: Discord.Client;
    private commandRegex = {} as RegexRecords;
    private prefixTestRegex = {} as RegexRecords;
    private commands = {} as Commands;

    constructor(private config: Config,
                public database: BotDatabase,
                public log: winston.LoggerInstance) {
        log.info('Populating regex dictionary.');
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

        this.client.login(config.discordToken);
    }

    private onReady(): () => void {
        return (() => {
            this.log.info(`Client is ready, username: ${this.client.user.username}.`);
            this.client.generateInvite([]).then((link) => this.log.info(`Bot invite link: ${link}`));

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
        });
    }

    private onMessage(): (msg: Discord.Message) => void {
        return ((msg: Discord.Message) => {
            let testRegex: RegExp = this.prefixTestRegex[msg.guild.id];
            let cmdRegex: RegExp = this.commandRegex[msg.guild.id];

            let cmdMatch = msg.cleanContent.match(cmdRegex);
            // Check if user called bot without command or command was not found
            if (!cmdMatch || !Object.keys(this.commands).includes(cmdMatch[1])) {
                if (msg.cleanContent.match(testRegex)) {
                    // User used prefix but command is invalid
                    // TODO(sam): List commands or pm user
                    msg.channel.send('I didn\'t understand you man.');
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

                if (typeof usage !== 'string') {
                    usage = usage.join('\n');
                }

                msg.channel.send('```\n' + usage + '\n```');
            }
        });
    }

    private guildCreate(): (guild: Discord.Guild) => void {
        return ((guild: Discord.Guild) => {
            guild.defaultChannel.send(`Hello! I am Safety Jim, \`${this.config.defaultPrefix}\` is my default prefix!`);
            this.database.createGuildPrefix(guild, this.config.defaultPrefix);
            this.createRegexForGuild(guild.id, this.config.defaultPrefix);
            this.log.info(`Joined guild ${guild.name}`);
        });
    }

    private createRegexForGuild(guildID: string, prefix: string) {
        this.commandRegex[guildID] = new RegExp(`^${prefix}\\s+([^\\s]+)\\s*([^]*)\\s*`, 'i');
        this.prefixTestRegex[guildID] = new RegExp(`^${prefix}[\\s]*( .*)?$`, 'i');
    }
}
