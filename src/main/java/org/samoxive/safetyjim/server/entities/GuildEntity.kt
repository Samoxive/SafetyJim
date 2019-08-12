package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.samoxive.safetyjim.tryhard

@Serializable
data class GuildEntity(
    val id: String,
    val name: String,
    val iconUrl: String
)

fun Guild.toGuildEntity(): GuildEntity = GuildEntity(id, name, tryhard { iconUrl } ?: "")