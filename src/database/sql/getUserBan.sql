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
WHERE GuildID = ? and BannedUserID = ?;