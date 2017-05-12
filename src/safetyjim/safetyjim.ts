import {Config} from '../config/config';
import * as log from 'winston';
import * as Discord from 'discord.js';
import { BotDatabase } from '../database/database';

export class SafetyJim {
    private client: Discord.Client;

    constructor(private config: Config, private database: BotDatabase) {
        this.client = new Discord.Client();
        this.client.on('ready', this.onReady());
        this.client.on('message', this.onMessage());

        this.client.login(config.discordToken);
    }

    private onReady(): () => void {
        return (() => {
            log.info(`Client is ready, username: ${this.client.user.username}.`);
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
}
