package org.samoxive.safetyjim.server.models

import org.samoxive.safetyjim.database.KickEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

data class KickModel(
    val id: Int,
    val user: UserModel,
    val moderatorUser: UserModel,
    val actionTime: Long,
    val reason: String,
    val pardoned: Boolean
)

suspend fun KickEntity.toKickModel(bot: DiscordBot): KickModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return KickModel(id, user.toUserModel(), moderatorUser.toUserModel(), kickTime, reason, pardoned)
}
