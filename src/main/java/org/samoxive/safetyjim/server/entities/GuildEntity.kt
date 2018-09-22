package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable

@Serializable
data class GuildEntity(
        val id: String,
        val name: String,
        val iconUrl: String
)