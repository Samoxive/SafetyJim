package org.samoxive.safetyjim.server.models

import net.dv8tion.jda.api.entities.TextChannel

data class ChannelModel(
    val id: String,
    val name: String
)

fun TextChannel.toChannelModel(): ChannelModel = ChannelModel(id, name)
