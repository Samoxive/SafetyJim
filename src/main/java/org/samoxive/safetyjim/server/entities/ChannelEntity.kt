package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.TextChannel

@Serializable
data class ChannelEntity(
    val id: String,
    val name: String
)

fun TextChannel.toChannelEntity(): ChannelEntity = ChannelEntity(id, name)