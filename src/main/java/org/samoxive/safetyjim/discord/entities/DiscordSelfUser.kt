package org.samoxive.safetyjim.discord.entities

import kotlinx.serialization.Serializable

@Serializable
data class DiscordSelfUser(
    val id: String,
    val username: String,
    val discriminator: String,
    val avatar: String
)