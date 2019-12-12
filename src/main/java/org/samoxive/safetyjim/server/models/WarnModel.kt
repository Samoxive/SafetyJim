package org.samoxive.safetyjim.server.models

import org.samoxive.safetyjim.database.WarnEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

data class WarnModel(
    val id: Int,
    val user: UserModel,
    val moderatorUser: UserModel,
    val actionTime: Long,
    val reason: String,
    val pardoned: Boolean
)

suspend fun WarnEntity.toWarnModel(bot: DiscordBot): WarnModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return WarnModel(id, user.toUserModel(), moderatorUser.toUserModel(), warnTime, reason, pardoned)
}
