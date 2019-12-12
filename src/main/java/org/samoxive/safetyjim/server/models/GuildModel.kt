package org.samoxive.safetyjim.server.models

import net.dv8tion.jda.api.entities.Guild

data class GuildModel(
    val id: String,
    val name: String,
    val iconUrl: String
)

fun Guild.toGuildModel(): GuildModel = GuildModel(id, name, iconUrl ?: "")
