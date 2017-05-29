interface IConfigFile {
    token: string;
    dbFileName: string;
    defaultPrefix: string;
}

export class Config {
    public discordToken: string;
    public dbFileName: string;
    public defaultPrefix: string;

    constructor(private configPath: string) {

        let configData = null;
        try {
            configData = require(this.configPath) as IConfigFile;
        } catch(e) {
            console.error("Loading config file failed with error: " + e.message);
            process.exit(e.code);
        }

        this.discordToken = configData.token;
        this.dbFileName = configData.dbFileName;
        this.defaultPrefix = configData.defaultPrefix;
    }
}
