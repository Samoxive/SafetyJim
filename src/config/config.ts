interface IConfigFile {
    token: string;
    dbFileName: string;
}

export class Config {
    public discordToken: string;
    public dbFileName: string;

    constructor(private configPath: string) {
        let configData = require(this.configPath) as IConfigFile;

        this.discordToken = configData.token;
        this.dbFileName = configData.dbFileName;
    }
}
