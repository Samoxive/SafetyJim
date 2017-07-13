CREATE TABLE BanList (
    BannedUserID      TEXT,
    BannedUserName    TEXT,
    ModeratorID       TEXT,
    ModeratorUserName TEXT,
    GuildID           TEXT,
    BanTime           INTEGER,
    ExpireTime        INTEGER,
    Reason            TEXT,
    Expires           BOOLEAN,
    Unbanned          BOOLEAN);

CREATE TABLE MuteList (
    MutedUserID       TEXT,
    MutedUserName     TEXT,
    ModeratorID       TEXT,
    ModeratorUserName TEXT,
    GuildID           TEXT,
    MuteTime          INTEGER,
    ExpireTime        INTEGER,
    Reason            INTEGER,
    Expires           BOOLEAN,
    Unmuted           BOOLEAN);

CREATE TABLE KickList (
    KickedUserID      TEXT,
    KickedUserName    TEXT,
    ModeratorID       TEXT,
    ModeratorUserName TEXT,
    GuildID           TEXT,
    KickTime          INTEGER,
    Reason            TEXT);

CREATE TABLE WarnList (
    WarnedUserID      TEXT,
    WarnedUserName    TEXT,
    ModeratorID       TEXT,
    ModeratorUserName TEXT,
    GuildID           TEXT,
    WarnTime          INTEGER,
    Reason            TEXT);

CREATE TABLE JoinList (
    UserID    TEXT,
    GuildID   TEXT,
    JoinTime  INTEGER,
    AllowTime INTEGER,
    Allowed   BOOLEAN);

CREATE TABLE Settings (
    GuildID TEXT NOT NULL,
    Key TEXT NOT NULL,
    Value TEXT
);

/* Possible Keys: ModLogActive, ModLogChannelID, HoldingRoomRoleID, HoldingRoomActive,
   HoldingRoomMinutes, HoldingRoomChannelID, EmbedColor, Prefix, WelcomeMessage */

CREATE INDEX IF NOT EXISTS "" ON BanList (
    ModeratorID,
    GuildID,
    BannedUserID);

CREATE INDEX IF NOT EXISTS "" ON JoinList (Allowed);