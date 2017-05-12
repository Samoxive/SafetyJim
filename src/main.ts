import * as Discord from 'discord.js';
import * as log from 'winston';
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase } from './database/database';
import { Config } from './config/config';
import { SafetyJim } from './safetyjim/safetyjim';

const config = new Config(path.join(__dirname, '..', 'config.json'));
const database = new BotDatabase(path.join(__dirname, '..', 'bot.db'));

database.init().then((db) => {
    const bot = new SafetyJim(config, db);
});
