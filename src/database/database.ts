import * as sqlite from 'sqlite';
import * as fs from 'fs';
import * as path from 'path';
import { Config } from '../config/config';
import { User, Guild } from 'discord.js';
import * as winston from 'winston';

function getSqlStatementFromFile(sqlFileName: string): string {
    return fs.readFileSync(path.join(__dirname, 'sql', sqlFileName)).toString();
}

export class BotDatabase {
    private database: sqlite.Database;

    constructor(private config: Config, private log: winston.LoggerInstance) {}

    // TODO (sam): This function doesn't need to return anything,
    // try to fix this later.
    public async init(): Promise<BotDatabase> {
        this.database = await sqlite.open(this.config.dbFileName);

        await this.database.run(`CREATE TABLE IF NOT EXISTS BanList (
                                    BannedUserID      TEXT,
                                    BannedUserName    TEXT,
                                    ModeratorID       TEXT,
                                    ModeratorUserName TEXT,
                                    GuildID           TEXT,
                                    BanTime           INTEGER,
                                    ExpireTime        INTEGER,
                                    Reason            TEXT,
                                    Expires           BOOLEAN,
                                    Unbanned          BOOLEAN);`)
                                    .catch((err) => { this.log.error('Could not create BanList table!'); });

        await this.database.run(`CREATE TABLE IF NOT EXISTS GuildSettings (
                                    GuildID TEXT PRIMARY KEY UNIQUE NOT NULL,
                                    ModLogActive         BOOLEAN,
                                    ModLogChannelID      TEXT,
                                    HoldingRoomRoleID    TEXT,
                                    HoldingRoomActive    BOOLEAN,
                                    HoldingRoomMinutes   INTEGER,
                                    HoldingRoomChannelID TEXT,
                                    EmbedColor TEXT);`)
                                .catch((err) => { this.log.error('Could not create GuildSettings table!'); });

        // TODO(sam): Index the BanList table
        /*
        await this.database.run('CREATE INDEX IF NOT EXISTS "" ON BanList (ModeratorID, GuildID, BannedUserID);')
                           .catch((err) => { this.log.error('Could not create index for Banlist table!'); });
        */
        await this.database.run('CREATE TABLE IF NOT EXISTS PrefixList (GuildID TEXT, Prefix TEXT);')
                           .catch((err) => { this.log.error('Could not create PrefixList table!'); });
        await this.database.run(`CREATE TABLE IF NOT EXISTS JoinList (
                                    UserID   TEXT,
                                    GuildID  TEXT,
                                    JoinTime INTEGER,
                                    AllowTime INTEGER,
                                    Allowed  BOOLEAN);`)
                                    .catch((err) => { this.log.error('Could not create JoinList table!'); });

        await this.database.run(`CREATE TABLE IF NOT EXISTS  KickList (
                                    KickedUserID      TEXT,
                                    KickedUserName    TEXT,
                                    ModeratorID       TEXT,
                                    ModeratorUserName TEXT,
                                    GuildID           TEXT,
                                    KickTime          INTEGER,
                                    Reason            TEXT);`)
                                    .catch((err) => { this.log.error('Could not create KickList table!'); });

        await this.database.run(`CREATE TABLE IF NOT EXISTS WarnList (
                                    WarnedUserID      TEXT,
                                    WarnedUserName    TEXT,
                                    ModeratorID       TEXT,
                                    ModeratorUserName TEXT,
                                    GuildID           TEXT,
                                    WarnTime          INTEGER,
                                    Reason            TEXT);`)
                                    .catch((err) => { this.log.error('Could not create WarnList table!'); });

        await this.database.run('CREATE INDEX IF NOT EXISTS "" ON JoinList (Allowed)')
                           .catch((err) => { this.log.error('Could not create index for JoinList table!'); });

        await this.database.run(`CREATE TABLE IF NOT EXISTS TagList (
                                    TagName         TEXT,
                                    TagResponse     TEXT,
                                    GuildID         TEXT);`)
                                    .catch((err) => { this.log.error('Could not create TagList table!'); });

        /* TODO: Index TagList?
        await this.database.run('CREATE INDEX IF NOT EXISTS "" on TagList (TagName, GuildID)')
                           .catch((err) => { this.log.error('Could not create index for TagList table!'); });
        */
        // seriously, fix this.
        return Promise.resolve(this);
    }

    public getModeratorsBans(modID: string, guildID: string): Promise<BanRecord[]> {
        return this.database.all('SELECT * FROM BanList WHERE ModeratorID = ? AND GuildID = ?;', modID, guildID)
            .then((rows) => rows as BanRecord[])
            .catch((err) => { this.log.error('Could not retrieve moderator ban records!'); });
    }

    public getGuildBans(guildID: string): Promise<BanRecord[]> {
        return this.database.all('SELECT * FROM BanList WHERE GuildID = ?;', guildID)
            .then((rows) => rows as BanRecord[])
            .catch((err) => { this.log.error('Could not retrieve guild ban records!'); });
    }

    public getUserBan(userID: string, guildID: string): Promise<BanRecord> {
        return this.database.get('SELECT * FROM BanList WHERE GuildID = ? and BannedUserID = ?;', guildID, userID)
            .then((row) => row as BanRecord)
            .catch((err) => { this.log.error('Could not retrieve user ban record!'); });
    }

    public getExpiredBans(): Promise<BanRecord[]> {
        return this.database.all(`SELECT * FROM BanList WHERE ExpireTime < (strftime(\'%s\',\'now\'))
                                                              and Expires = 1 and Unbanned = 0;`)
            .then((rows) => rows as BanRecord[])
            .catch((err) => { this.log.error('Could not retrieve expired ban records!'); });
    }

    public getModeratorsKicks(modID: string, guildID: string): Promise<KickRecord[]> {
        return this.database.all('SELECT * FROM KickList WHERE ModeratorID = ? AND GuildID = ?;', modID, guildID)
                            .then((rows) => rows as KickRecord[])
                            .catch((err) => { this.log.error('Could not retrieve moderator kick records'); });
    }

    public getGuildKicks(guildID: string): Promise<KickRecord[]> {
        return this.database.all('SELECT * FROM KickList WHERE GuildID = ?;', guildID)
            .then((rows) => rows as KickRecord[])
            .catch((err) => { this.log.error('Could not retrieve guild kick records!'); });
    }

    public getUserKick(userID: string, guildID: string): Promise<KickRecord> {
        return this.database.get('SELECT * FROM KickList WHERE GuildID = ? and KickedUserID = ?;', guildID, userID)
            .then((row) => row as KickRecord)
            .catch((err) => { this.log.error('Could not retrieve user kick record!'); });
    }

    public getModeratorsWarns(modID: string, guildID: string): Promise<WarnRecord[]> {
        return this.database.all('SELECT * FROM WarnList WHERE ModeratorID = ? AND GuildID = ?;', modID, guildID)
                            .then((rows) => rows as WarnRecord[])
                            .catch((err) => { this.log.error('Could not retrieve moderator warning records'); });
    }

    public getGuildWarns(guildID: string): Promise<WarnRecord[]> {
        return this.database.all('SELECT * FROM WarnList WHERE GuildID = ?;', guildID)
            .then((rows) => rows as WarnRecord[])
            .catch((err) => { this.log.error('Could not retrieve guild warning records!'); });
    }

    public getUserWarn(userID: string, guildID: string): Promise<WarnRecord> {
        return this.database.get('SELECT * FROM WarnList WHERE GuildID = ? and WarnedUserID = ?;', guildID, userID)
            .then((row) => row as WarnRecord)
            .catch((err) => { this.log.error('Could not retrieve user warning record!'); });
    }

    public getGuildPrefix(guild: Guild): Promise<string> {
        return this.database.get('SELECT Prefix from PrefixList WHERE GuildID = ?', guild.id)
            .then((row) => row.Prefix)
            .catch((err) => { this.log.error('Could not retrieve prefix record!'); });
    }

    public getGuildPrefixes(): Promise<PrefixRecord[]> {
        return this.database.all('SELECT * from PrefixList;')
            .then((rows) => rows as PrefixRecord[])
            .catch((err) => { this.log.error('Could not retrieve prefix recods!'); });
    }

    public getGuildConfiguration(guild: Guild): Promise<GuildConfig> {
        return this.database.get('SELECT * FROM GuildSettings WHERE GuildID = ?', guild.id)
                            .then((row) => row as GuildConfig)
                            .catch((err) => { this.log.error('Could not retrieve guild config!'); });
    }

    public getGuildConfigurations(): Promise<GuildConfig[]> {
        return this.database.all('SELECT * FROM GuildSettings;')
                            .then((rows) => rows as GuildConfig[])
                            .catch((err) => { this.log.error('Could not retrieve guild configs!'); });
    }

    public getUsersThatCanBeAllowed(): Promise<JoinRecord[]> {
        return this.database.all('SELECT * FROM JoinList WHERE AllowTime < (strftime(\'%s\',\'now\')) and Allowed = 0')
            .then((rows) => rows as JoinRecord[])
            .catch((err) => { this.log.error('Could not retrieve users that can be allowed!'); });
    }

    public getTagResponse(tagName: string, guild: Guild): Promise<string> {
        return this.database.get('SELECT TagResponse FROM TagList WHERE TagName = ? and GuildID = ?',
            tagName, guild.id)
            .then((response) => response.TagResponse);
    }

    public getAllTags(guild: Guild): Promise<TagRecord[]> {
        return this.database.all('SELECT * FROM TagList WHERE GuildID = ?', guild.id)
            .then((rows) => rows as TagRecord[])
            .catch((err) => { this.log.error('Could not retrieve tags!'); });
    }

    public updateGuildPrefix(guild: Guild, newPrefix: string): void {
        this.database.run('UPDATE PrefixList SET Prefix = ? WHERE GuildID = ?', newPrefix, guild.id)
            .catch((err) => { this.log.error('Could not update prefix record!'); });
    }

    // tslint:disable-next-line:variable-name
    public async updateGuildConfig(guild: Guild,
                                   options: {holdingRoomRoleID?: string, embedColor?: string,
                                             modLog?: boolean, modLogChannelID?: string,
                                             holdingRoom?: boolean, minutes?: number,
                                             holdingRoomID?: string}): Promise<void> {
        let origConfig = await this.getGuildConfiguration(guild);

        let holdingRoomActive: boolean;
        let modLogActive;

        if (options.holdingRoom === true) {
            holdingRoomActive = true;
        } else if (options.holdingRoom === false) {
            holdingRoomActive = false;
        } else {
            holdingRoomActive = origConfig.HoldingRoomActive === 1;
        }

        if (options.modLog === true) {
            modLogActive = true;
        } else if (options.modLog === false) {
            modLogActive = false;
        } else {
            modLogActive = origConfig.ModLogActive === 1;
        }

        this.database.run(`UPDATE GuildSettings
                            SET ModLogActive = ?, ModLogChannelID = ?,
                            HoldingRoomRoleID = ?, HoldingRoomActive = ?, HoldingRoomMinutes = ?,
                            HoldingRoomChannelID = ?, EmbedColor = ? WHERE GuildID = ?;`,
                            modLogActive,
                            options.modLogChannelID || origConfig.ModLogChannelID,
                            options.holdingRoomRoleID || origConfig.HoldingRoomRoleID,
                            holdingRoomActive,
                            options.minutes || origConfig.HoldingRoomMinutes,
                            options.holdingRoomID || origConfig.HoldingRoomChannelID,
                            options.embedColor || origConfig.EmbedColor,
                            guild.id)
                            .catch((e) => { this.log.error('Could not update GuildSettings!'); });
    }

    public updateJoinRecord(jRecord: JoinRecord) {
        this.database.run(`UPDATE JoinList SET Allowed = ? WHERE UserID = ? and GuildID = ?`,
                          true, jRecord.UserID, jRecord.GuildID)
                          .catch((err) => { this.log.error('Could not update JoinRecord!'); });
    }

    public updateBanRecord(bRecord: BanRecord) {
        this.database.run(`UPDATE BanList SET Unbanned = ? WHERE BannedUserID = ? and GuildID = ?`,
                          true, bRecord.BannedUserID, bRecord.GuildID)
                          .catch((err) => { this.log.error('Could not update BanRecord!'); });
    }

    public updateTagResponse(name: string, response: string, guild: Guild) {
        this.database.run(`UPDATE TagList SET TagResponse = ? WHERE TagName = ? and GuildID = ?`,
            response, name, guild.id)
            .catch((err) => { this.log.error('Could not update TagResponses!'); });
    }

    public delGuildPrefix(guild: Guild): void {
        this.database.run('DELETE FROM PrefixList WHERE GuildID = ?', guild.id)
            .catch((err) => { this.log.error('Could not delete prefix record!'); });
    }

    public delGuildSettings(guild: Guild): void {
        this.database.run('DELETE FROM GuildSettings WHERE GuildID = ?', guild.id)
                     .catch((err) => { this.log.error('Could not delete guild settings!'); });
    }

    public delUserBan(userID: string, guildID: string): void {
        this.database.run('DELETE FROM BanList WHERE UserID = ? AND GuildID = ?;', userID, guildID)
            .catch((err) => { this.log.error('Could not delete ban record!'); });
    }

    public delUserKick(userID: string, guildID: string): void {
        this.database.run('DELETE FROM KickList WHERE UserID = ? AND GuildID = ?;', userID, guildID)
            .catch((err) => { this.log.error('Could not delete kick record!'); });
    }

    public delUserWarn(userID: string, guildID: string): void {
        this.database.run('DELETE FROM WarnList WHERE UserID = ? AND GuildID = ?;', userID, guildID)
            .catch((err) => { this.log.error('Could not delete warn record!'); });
    }

    public delJoinEntry(userID: string, guildID: string): void {
        this.database.run('DELETE FROM JoinList WHERE UserID = ? AND GuildID = ?;', userID, guildID)
                     .catch((err) => { this.log.error('Could not delete join record!'); });
    }

    public delTagResponse(name: string, guild: Guild) {
        this.database.run('DELETE FROM TagList WHERE TagName = ? AND GuildID = ?', name, guild.id)
                        .catch((err) => { this.log.error('Could not delete join record!'); });
    }

    public createGuildPrefix(guild: Guild, prefix: string): void {
        this.database.run('INSERT INTO PrefixList (GuildID, Prefix) VALUES (?, ?);', guild.id, prefix)
            .catch((err) => { this.log.error('Could not create prefix record!'); });
    }

    public createJoinRecord(user: User, guild: Guild, minutes: number): void {
        let now = Math.round((new Date()).getTime() / 1000);
        this.database.run(`INSERT INTO JoinList (UserId, GuildID, JoinTime, AllowTime, Allowed)
                            VALUES (?, ?, ?, ?, ?)`,
                            user.id, guild.id, now, now + minutes * 60, false)
                        .catch((err) => { this.log.error('Could not create join record!'); });
    }

    public createGuildSettings(guild: Guild): void {
        this.database.run(`INSERT INTO GuildSettings (GuildID, ModLogActive, ModLogChannelID,
                            HoldingRoomRoleID, HoldingRoomActive, HoldingRoomMinutes, HoldingRoomChannelID, EmbedColor)
                            VALUES(?, ?, ?, ?, ?, ?, ?, ?);`, guild.id, false, guild.defaultChannel.id,
                                                  null, false, 3, guild.defaultChannel.id, '4286f4')
                          .catch((err) => { this.log.error('Could not create guild settings!'); });
    }

    public createUserWarn(warnedUser: User, modUser: User, guild: Guild, reason: string): void {
        let now = Math.round((new Date()).getTime() / 1000);

        this.database.run(`INSERT INTO WarnList
                            (WarnedUserID, WarnedUserName, ModeratorID, ModeratorUserName, GuildID, WarnTime, Reason)
                          VALUES(?, ?, ?, ?, ?, ?, ?);`, warnedUser.id, warnedUser.tag,
                          modUser.id, modUser.tag, guild.id, now, reason)
                          .catch((err) => { this.log.error('Could not create a warning record!'); });
    }

    public createUserKick(kickedUser: User, modUser: User, guild: Guild, reason: string): void {
        let now = Math.round((new Date()).getTime() / 1000);

        this.database.run(`INSERT INTO KickList
                            (KickedUserID, KickedUserName, ModeratorID, ModeratorUserName, GuildID, KickTime, Reason)
                          VALUES(?, ?, ?, ?, ?, ?, ?);`, kickedUser.id, kickedUser.tag,
                          modUser.id, modUser.tag, guild.id, now, reason)
                          .catch((err) => { this.log.error('Could not create a kick record!'); });
    }

    public createUserBan(bannedUser: User,
                         modUser: User,
                         guild: Guild,
                         reason: string,
                         expireTime?: number): void {
        let now = Math.round((new Date()).getTime() / 1000);
        let expires = true;

        if (expireTime == null) {
            expires = false;
            expireTime = 0;
        }

        this.database.run(`INSERT INTO BanList (
                            BannedUserID,
                            BannedUserName,
                            ModeratorID,
                            ModeratorUserName,
                            GuildID,
                            BanTime,
                            ExpireTime,
                            Reason,
                            Expires,
                            Unbanned)
                          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);`,
                          bannedUser.id,
                          bannedUser.tag,
                          modUser.id,
                          modUser.tag,
                          guild.id,
                          now,
                          expireTime,
                          reason,
                          expires,
                          false)
                      .catch((err) => { this.log.error('Could not create a ban record!'); });
    }

    public createTag(name: string, response: string, guild: Guild) {
        this.database.run(`INSERT INTO TagList (TagName, TagResponse, GuildID) VALUES (?, ?, ?);`,
            name, response, guild.id)
            .catch((err) => { this.log.error('Could not create a Tag!'); });
    }
}

export interface GuildConfig {
    GuildID: string;
    ModLogActive: number;
    ModLogChannelID: string;
    HoldingRoomRoleID: string;
    HoldingRoomActive: number;
    HoldingRoomMinutes: number;
    HoldingRoomChannelID: string;
    EmbedColor: string;
}

export interface BanRecord {
    BannedUserID: string;
    BannedUserName: string;
    ModeratorID: string;
    ModeratorUserName: string;
    GuildID: string;
    BanTime: number;
    ExpireTime: number;
    Reason: string;
    Expires: number;
}

export interface KickRecord {
    KickedUserID: string;
    KickedUserName: string;
    ModeratorID: string;
    ModeratorUserName: string;
    GuildID: string;
    KickTime: number;
    Reason: string;
}

export interface WarnRecord {
    WarnedUserID: string;
    WarnedUserName: string;
    ModeratorID: string;
    ModeratorUserName: string;
    GuildID: string;
    WarnTime: number;
    Reason: string;
}

export interface PrefixRecord {
    GuildID: string;
    Prefix: string;
}

export interface JoinRecord {
    UserID: string;
    GuildID: string;
    JoinTime: number;
    Allowed: number;
}

export interface TagRecord {
    TagName: string;
    TagResponse: string;
    GuildID: string;
}
