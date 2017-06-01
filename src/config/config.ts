import * as winston from 'winston';

let defaultConfigDbName = 'database.db';
let defaultConfigPrefix = '!';

interface IConfigFile {
    token: string;
    dbFileName: string;
    defaultPrefix: string;
}

export class Config {
    public discordToken: string;
    public dbFileName: string;
    public defaultPrefix: string;

    constructor(private configPath: string, private log: winston.LoggerInstance) {

        let configData = null;
        try {
            configData = require(this.configPath) as IConfigFile;
        } catch (e) {
            log.error(`Loading config file failed with error: \`${e.message}\``);
            process.exit(e.code);
        }

        this.discordToken = configData.token;
        if (this.discordToken === undefined) {
            log.error('Discord Token not provided!');
            process.exit(1);
        }

        this.dbFileName = configData.dbFileName;
        if (this.dbFileName === undefined) {
            log.error(`Database file name not provided!\nUsing \`${defaultConfigDbName}\` as default!`);
            this.dbFileName = defaultConfigDbName;
        }

        this.defaultPrefix = configData.defaultPrefix;
        if (this.defaultPrefix === undefined) {
            log.error(`Default prefix not provided!\nUsing \` ${defaultConfigPrefix} \` as default!`);
            this.defaultPrefix = defaultConfigPrefix;
        }
    }
}
