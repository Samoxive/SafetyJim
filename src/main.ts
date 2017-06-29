import * as Discord from 'discord.js';
import * as winston from 'winston';
require('winston-daily-rotate-file');
import * as fs from 'fs';
import * as path from 'path';
import { BotDatabase } from './database/database';
import { Config } from './config/config';
import { SafetyJim } from './safetyjim/safetyjim';

const log = new winston.Logger({
    transports: [
        new winston.transports.Console({
            level: 'debug',
            handleExceptions: true,
            json: false,
            colorize: true,
        }),
        new winston.transports.DailyRotateFile({
            level: 'debug',
            handleExceptions: true,
            json: false,
            filename: './logs/.log',
            zippedArchive: true,
            prepend: true,
            datePattern: 'yyyy-MM-dd',
        }),
    ],
});

const config = new Config(path.join(__dirname, '..', 'config.json'), log);
const database = new BotDatabase(config, log);
database.init().then((db) => {
    const bot = new SafetyJim(config, db, log);
});

process.addListener('uncaughtException', (err) => {
    log.error(`Uncaught Exception: ${err.message} : ${err.stack}`);
});

process.addListener('unhandledRejection', (err) => {
    let isErr = (err.stack == null) || (err.message == null);

    if (isErr) {
        log.error(`Uncaught Rejection: ${err.message} : ${err.stack}`);
    } else {
        log.error(`Uncaught Rejection: ${JSON.stringify(err)}`);
    }
});
