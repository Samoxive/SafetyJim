package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable

@Serializable
data class SelfUserEntity(
        val id: String,
        val name: String,
        val avatarUrl: String,
        val guilds: List<GuildEntity>
)