package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable

@Serializable
data class GuildSettingsEntity(
        val guild: GuildEntity,
        val modLog: Boolean,
        val modLogChannel: ChannelEntity,
        val holdingRoom: Boolean,
        val holdingRoomRole: RoleEntity?,
        val holdingRoomMinutes: Int,
        val inviteLinkRemover: Boolean,
        val welcomeMessage: Boolean,
        val message: String,
        val welcomeMessageChannel: ChannelEntity,
        val prefix: String,
        val silentCommands: Boolean,
        val noSpacePrefix: Boolean,
        val statistics: Boolean
)