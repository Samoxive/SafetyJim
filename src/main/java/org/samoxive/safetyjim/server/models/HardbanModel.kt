package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import org.samoxive.safetyjim.database.HardbanEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

@Serializable
data class HardbanModel(
    val id: Int,
    val user: UserModel,
    val moderatorUser: UserModel,
    val actionTime: Long,
    val reason: String
)

suspend fun HardbanEntity.toHardbanModel(bot: DiscordBot): HardbanModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return HardbanModel(id, user.toUserModel(), moderatorUser.toUserModel(), hardbanTime, reason)
}