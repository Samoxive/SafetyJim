import {Config} from '../config/config';
import * as winston from 'winston';
import * as Discord from 'discord.js';
import { BotDatabase } from '../database/database';

export class SafetyJim {
    private client: Discord.Client;

    constructor(private config: Config, private database: BotDatabase, public log: winston.LoggerInstance) {
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
        });
    }

    private onMessage(): (msg: Discord.Message) => void {
        return ((msg: Discord.Message) => {
            if (msg.channel.type === 'dm' || msg.author.bot) {
                return;
            }
            if (msg.content === 'ping') {
                msg.channel.send('pong', {reply: msg.author});
            }
        });
    }

    private guildCreate(): (guild: Discord.Guild) => void {
        return ((guild: Discord.Guild) => {
            guild.defaultChannel.send(`Hello! I am Safety Jim, \`${this.config.defaultPrefix}\` is my default prefix!`);
            this.database.createGuildPrefix(guild, this.config.defaultPrefix);
            this.log.info(`Joined guild ${guild.name}`);
        });
    }
}
