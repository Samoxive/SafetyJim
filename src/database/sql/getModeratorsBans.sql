SELECT BannedUserID,
    BannedUserName,
    ModeratorID,
    ModeratorUserName,
    GuildID,
    BanTime,
    ExpireTime,
    Reason,
    Expires
FROM BanList
WHERE ModeratorID = ?
    AND GuildID = ?;