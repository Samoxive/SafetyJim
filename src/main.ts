import * as Discord from 'discord.js';
import * as log from 'winston';
import * as fs from 'fs';
import { BotDatabase } from './database/database';

const client = new Discord.Client();
const config = require('./config.json') as IConfig;

const db: BotDatabase = new BotDatabase('test.db');

interface IConfig {
    token: string;
}

client.on('ready', () => {
    log.info(`Client is ready, username: ${client.user.username}.`);
});

client.on('message', (msg) => {
    if (msg.channel.type === 'dm') {
        return;
    }
    if (msg.content === 'ping') {
        msg.channel.send('pong', { reply: msg.author });
    }
});

db.init();
client.login(config.token);
