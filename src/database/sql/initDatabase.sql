CREATE TABLE IF NOT EXISTS BanList (
    BannedUserID      TEXT,
    BannedUserName    TEXT,
    ModeratorID       TEXT,
    ModeratorUserName TEXT,
    GuildID           TEXT,
    BanTime           INTEGER,
    ExpireTime        INTEGER,
    Reason            TEXT,
    Expires           BOOLEAN);

CREATE INDEX IF NOT EXISTS "" ON BanList (
    ModeratorID,
    GuildID,
    BannedUserID);