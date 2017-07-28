import * as winston from 'winston';
import * as toml from 'toml';
import * as fs from 'fs';
import { Validator, validate } from 'jsonschema';

const configSchema = {
    id: 'Config',
    type: 'object',
    properties: {
        jim: {
            type: 'object',
            properties: {
                token: { type: 'string' },
                default_prefix: { type: 'string' },
            },
        },
        database: {
            type: 'object',
            properties: {
                name: { type: 'string' },
            },
        },
        botlist: {
            type: 'object',
            properties: {
                enabled: { type: 'boolean' },
                list: {
                    type: 'array',
                    items: {
                        type: 'object',
                        properties: {
                            name: { type: 'string' },
                            url: { type: 'string' },
                            token: { type: 'string' },
                            ignore_errors: { type: 'boolean' },
                        },
                    },
                },
            },
        },
    },
};

export class Config {
    public jim: Jim;
    public database: Database;
    public botlist: BotList;
    public version: string;

    constructor(private configPath: string, private log: winston.LoggerInstance) {
        let tomlString;
        try {
            tomlString = fs.readFileSync('config.toml', 'utf8');
        } catch (e) {
            log.error(`Loading config file failed with error: ${e}`);
            process.exit(-1);
        }

        let tomlConfig = toml.parse(tomlString) as TomlConfig;
        tomlConfig.botlist.list = tomlConfig.botlist.list || [];

        let validator = new Validator();
        try {
            // Typescript error for options can be ignored, project maintainer
            // isn't active, typing file isn't fixed
            validator.validate(tomlConfig, configSchema, { throwError: true });
        } catch (e) {
            this.log.error(`Invalid configuration file! ${e}`);
        }

        let packageData;
        try {
            packageData = require('../../package.json');
        } catch (e) {
            log.error(`Loading package file failed with error: \`${e.message}\``);
            process.exit(e.code);
        }

        this.version = packageData.version || 'Unspecified version';
        this.jim = tomlConfig.jim;
        this.database = tomlConfig.database;
        this.botlist = tomlConfig.botlist;
    }
}

interface TomlConfig {
    jim: Jim;
    database: Database;
    botlist: BotList;
}

interface Jim {
    token: string;
    default_prefix: string;
}

interface Database {
    name: string;
}

interface BotList {
    enabled: boolean;
    list: BotListMetadata[];
}

interface BotListMetadata {
    name: string;
    url: string;
    token: string;
    ignore_errors: boolean;
}
