import * as Discord from 'discord.js';
import * as winston from 'winston';
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase } from './database/database';
import { Config } from './config/config';
import { SafetyJim } from './safetyjim/safetyjim';

const config = new Config(path.join(__dirname, '..', 'config.json'));
const log = new winston.Logger({
    transports: [
        new winston.transports.Console({
            level: 'debug',
            handleExceptions: true,
            json: false,
            colorize: true,
        }),
    ],
});

const database = new BotDatabase(config, log);
database.init().then((db) => {
    const bot = new SafetyJim(config, db, log);
});
