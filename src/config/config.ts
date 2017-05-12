interface IConfigFile {
    token: string;
}

export class Config {
    public discordToken: string;

    constructor(private configPath: string) {
        let configData = require(this.configPath) as IConfigFile;

        this.discordToken = configData.token;
    }
}
