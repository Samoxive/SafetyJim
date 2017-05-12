import * as sqlite from 'sqlite';

export class BotDatabase {
    private database: sqlite.Database;

    constructor(private dbPath: string) {}

    // TODO (sam): This function doesn't need to return anything,
    // try to fix this later.
    public async init(): Promise<sqlite.Database> {
        this.database = await sqlite.open(this.dbPath);

        this.database.run('CREATE TABLE IF NOT EXISTS BanList (' +
            'BannedUserID      TEXT    PRIMARY KEY ON CONFLICT FAIL,' +
            'BannedUserName    TEXT,' +
            'ModeratorID       TEXT,' +
            'ModeratorUserName TEXT,' +
            'ServerID          TEXT,' +
            'BanTime           INTEGER,' +
            'ExpireTime        INTEGER,' +
            'Reason            TEXT,' +
            'Expires           BOOLEAN);');

        // seriously, fix this.
        return Promise.resolve(this.database);
    }
}
