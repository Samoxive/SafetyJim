import * as winston from 'winston';

let defaultConfigDbName = 'database.db';
let defaultConfigPrefix = '!';
let defaultConfigVersion = '1.0.0';

interface IConfigFile {
    token: string;
    dbFileName: string;
    defaultPrefix: string;
    discordbotsToken: string;
    discordbotspwToken: string;
}

export class Config {
    public discordToken: string;
    public dbFileName: string;
    public defaultPrefix: string;
    public discordbotsToken: string;
    public discordbotspwToken: string;
    public version: string;

    constructor(private configPath: string, private log: winston.LoggerInstance) {

        let configData = null;
        try {
            configData = require(this.configPath) as IConfigFile;
        } catch (e) {
            log.error(`Loading config file failed with error: \`${e.message}\``);
            process.exit(e.code);
        }

        let packageData = null;
        try {
            packageData = require('../../package.json');
        } catch (e) {
            log.error(`Loading package file failed with error: \`${e.message}\``);
            process.exit(e.code);
        }

        this.discordToken = configData.token;
        if (this.discordToken === undefined) {
            log.error('Discord Token not provided!');
            process.exit(1);
        }

        this.dbFileName = configData.dbFileName;
        if (this.dbFileName === undefined) {
            log.error(`Database file name not provided! Using \`${defaultConfigDbName}\` as default!`);
            this.dbFileName = defaultConfigDbName;
        }

        this.defaultPrefix = configData.defaultPrefix;
        if (this.defaultPrefix === undefined) {
            log.error(`Default prefix not provided! Using \` ${defaultConfigPrefix} \` as default!`);
            this.defaultPrefix = defaultConfigPrefix;
        }

        this.discordbotsToken = configData.discordbotsToken;
        if (this.discordbotsToken === undefined) {
            log.error(`Default discordbotsToken not provided! Using \` "" \` as default!`);
            this.discordbotsToken = '';
        }

        this.discordbotspwToken = configData.discordbotspwToken;
        if (this.discordbotspwToken === undefined) {
            log.error(`Default discordbotspwToken not provided! Using \` "" \` as default!`);
            this.discordbotspwToken = '';
        }

        this.version = packageData.version;
        if (this.version === undefined) {
            log.error(`Default prefix not provided! Using \` ${defaultConfigVersion} \` as default!`);
            this.version = defaultConfigVersion;
        }
    }
}
