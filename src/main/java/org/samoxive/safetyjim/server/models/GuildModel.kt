package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.Guild
import org.samoxive.safetyjim.tryhard

@Serializable
data class GuildModel(
    val id: String,
    val name: String,
    val iconUrl: String
)

fun Guild.toGuildModel(): GuildModel = GuildModel(id, name, iconUrl ?: "")