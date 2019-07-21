package org.samoxive.safetyjim.server.entities

import kotlinx.serialization.Serializable

@Serializable
data class GuildSettingsEntity(
        val guild: GuildEntity,
        val channels: List<ChannelEntity>,
        val roles: List<RoleEntity>,
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
        val statistics: Boolean,
        val joinCaptcha: Boolean,
        val silentCommandsLevel: Int,
        val modActionConfirmationMessage: Boolean,
        val wordFilter: Boolean,
        val wordFilterBlacklist: String?,
        val wordFilterLevel: Int
)