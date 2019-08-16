package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import org.samoxive.safetyjim.database.MuteEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

@Serializable
data class MuteModel(
    val id: Int,
    val user: UserModel,
    val moderatorUser: UserModel,
    val actionTime: Long,
    val expirationTime: Long?,
    val unmuted: Boolean,
    val reason: String
)

suspend fun MuteEntity.toMuteModel(bot: DiscordBot): MuteModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return MuteModel(id, user.toUserModel(), moderatorUser.toUserModel(), muteTime, expireTime, unmuted, reason)
}