let defaultConfigDbName = "database.db";
let defaultConfigPrefix = "!"

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
            // Log with Winston?
            console.error("Loading config file failed with error: " + e.message);
            process.exit(e.code);
        }
        
        this.discordToken = configData.token;
        if(this.discordToken == undefined) {
            console.error("Discord Token not provided!");
            process.exit(1);
        }

        this.dbFileName = configData.dbFileName;
        if(this.dbFileName == undefined) {
            console.error("Database file name not provided!\nUsing '" + defaultConfigDbName + "'  as default!");
            this.dbFileName = defaultConfigDbName;
        }

        this.defaultPrefix = configData.defaultPrefix;
        if(this.defaultPrefix == undefined) {
            console.error("Default prefix not provided!\nUsing '" + defaultConfigPrefix + "' as default!");
            this.defaultPrefix = defaultConfigPrefix;
        }
    }
}
