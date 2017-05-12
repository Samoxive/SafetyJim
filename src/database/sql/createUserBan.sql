INSERT INTO BanList (
    BannedUserID,
    BannedUserName,
    ModeratorID,
    ModeratorUserName,
    GuildID,
    BanTime,
    ExpireTime,
    Reason,
    Expires
) VALUES (
    ? ,
    ? ,
    ? ,
    ? ,
    ? ,
    ? ,
    ? ,
    ? ,
    ? );