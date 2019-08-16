package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SelfUserModel(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val guilds: List<GuildModel>
)