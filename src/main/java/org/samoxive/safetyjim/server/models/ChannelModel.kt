package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.entities.TextChannel

@Serializable
data class ChannelModel(
    val id: String,
    val name: String
)

fun TextChannel.toChannelModel(): ChannelModel = ChannelModel(id, name)
