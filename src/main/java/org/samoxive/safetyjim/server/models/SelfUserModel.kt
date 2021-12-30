package org.samoxive.safetyjim.server.models

data class SelfUserModel(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val guilds: List<GuildModel>
)
