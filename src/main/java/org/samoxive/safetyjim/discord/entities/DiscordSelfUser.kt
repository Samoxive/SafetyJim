package org.samoxive.safetyjim.discord.entities

data class DiscordSelfUser(
    val id: String,
    val username: String,
    val discriminator: String,
    val avatar: String?
)
