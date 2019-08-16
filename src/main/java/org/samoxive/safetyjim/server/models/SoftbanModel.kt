package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.samoxive.safetyjim.database.SoftbanEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.await

@Serializable
data class SoftbanModel(
        val id: Int,
        val user: UserModel,
        val moderatorUser: UserModel,
        val actionTime: Long,
        val reason: String
)

suspend fun SoftbanEntity.toSoftbanModel(bot: DiscordBot): SoftbanModel {
    val shard = bot.getGuildShard(guildId)
    val user = shard.retrieveUserById(userId).await()
    val moderatorUser = shard.retrieveUserById(moderatorUserId).await()

    return SoftbanModel(id, user.toUserModel(), moderatorUser.toUserModel(), softbanTime, reason)
}