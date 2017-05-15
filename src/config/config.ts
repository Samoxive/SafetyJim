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
        let configData = require(this.configPath) as IConfigFile;

        this.discordToken = configData.token;
        this.dbFileName = configData.dbFileName;
        this.defaultPrefix = configData.defaultPrefix;
    }
}
