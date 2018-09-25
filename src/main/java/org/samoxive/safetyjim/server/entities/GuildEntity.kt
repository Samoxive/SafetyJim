package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable
import net.dv8tion.jda.core.entities.Guild

@Serializable
data class GuildEntity(
        val id: String,
        val name: String,
        val iconUrl: String
)

fun Guild.toGuildEntity(): GuildEntity = GuildEntity(id, name, iconUrl)