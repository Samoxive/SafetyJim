import {Config} from '../config/config';
import * as log from 'winston';
import * as Discord from 'discord.js';
import * as sqlite from 'sqlite';

export class SafetyJim {
    private client: Discord.Client;

    constructor(private config: Config, private database: sqlite.Database) {
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
            if (msg.channel.type === 'dm') {
                return;
            }
            if (msg.content === 'ping') {
                msg.channel.send('pong', {reply: msg.author});
            }
        });
    }
}
