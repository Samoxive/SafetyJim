package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import org.samoxive.safetyjim.database.BanEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

@Serializable
data class BanModel(
    val id: Int,
    val user: UserModel,
    val moderatorUser: UserModel,
    val actionTime: Long,
    val expirationTime: Long,
    val unbanned: Boolean,
    val reason: String
)

suspend fun BanEntity.toBanModel(bot: DiscordBot): BanModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return BanModel(id, user.toUserModel(), moderatorUser.toUserModel(), banTime, expireTime, unbanned, reason)
}
