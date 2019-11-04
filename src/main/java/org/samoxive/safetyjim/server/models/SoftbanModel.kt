package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import org.samoxive.safetyjim.database.SoftbanEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

@Serializable
data class SoftbanModel(
    val id: Int,
    val user: UserModel,
    val moderatorUser: UserModel,
    val actionTime: Long,
    val reason: String,
    val pardoned: Boolean
)

suspend fun SoftbanEntity.toSoftbanModel(bot: DiscordBot): SoftbanModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return SoftbanModel(id, user.toUserModel(), moderatorUser.toUserModel(), softbanTime, reason, pardoned)
}
