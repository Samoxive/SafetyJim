import * as sqlite from 'sqlite';
import * as fs from 'fs';
import * as path from 'path';
import { Config } from '../config/config';
import { User, Guild } from 'discord.js';

function getSqlStatementFromFile(sqlFileName: string): string {
    return fs.readFileSync(path.join(__dirname, 'sql', sqlFileName)).toString();
}

export class BotDatabase {
    private database: sqlite.Database;
    private sqlStatements;
    constructor(private config: Config) {
        this.sqlStatements = {
            initDatabase: getSqlStatementFromFile('initDatabase.sql'),
            getModeratorsBans: getSqlStatementFromFile('getModeratorsBans.sql'),
            getGuildBans: getSqlStatementFromFile('getGuildBans.sql'),
            getUserBan: getSqlStatementFromFile('getUserBan.sql'),
            delUserBan: getSqlStatementFromFile('delUserBan.sql'),
            createUserBan: getSqlStatementFromFile('createUserBan.sql'),
        };
    }

    // TODO (sam): This function doesn't need to return anything,
    // try to fix this later.
    public async init(): Promise<BotDatabase> {
        this.database = await sqlite.open(this.config.dbFileName);

        await this.database.run(this.sqlStatements.initDatabase);

        // seriously, fix this.
        return Promise.resolve(this);
    }

    public getModeratorsBans(modID: string, guildID: string): Promise<BanRecord[]> {
        return this.database.all(this.sqlStatements.getModeratorsBans, modID, guildID)
            .then((rows) => rows as BanRecord[]);
    }

    public getGuildBans(guildID: string): Promise<BanRecord[]> {
        return this.database.all(this.sqlStatements.getModeratorsBans, guildID)
            .then((rows) => rows as BanRecord[]);
    }

    public getUserBan(userID: string, guildID: string): Promise<BanRecord> {
        return this.database.get(this.sqlStatements.getUserBan, userID, guildID)
            .then((row) => row as BanRecord);
    }

    public delUserBan(userID: string, guildID: string): void {
        this.database.run(this.sqlStatements.delUserBan, userID, guildID);
    }

    public createUserBan(bannedUser: User,
                         modUser: User,
                         guild: Guild,
                         reason: string,
                         expireTime?: number): void {
        let expires = true;

        if (expireTime == null) {
            expires = false;
            expireTime = 0;
        }

        this.database.run(this.sqlStatements.createUserBan,
                          bannedUser.id,
                          bannedUser.username + bannedUser.discriminator,
                          modUser.id,
                          modUser.username + modUser.discriminator,
                          guild.id,
                          (new Date()).getSeconds(),
                          expireTime,
                          reason,
                          expires);
    }
}

interface BanRecord {
    BannedUserID: string;
    BannedUserName: string;
    ModeratorID: string;
    ModeratorUserName: string;
    GuildID: string;
    BanTime: number;
    ExpireTime: number;
    Reason: string;
    Expires: boolean;
}
