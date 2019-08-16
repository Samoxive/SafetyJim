package org.samoxive.safetyjim.server.models

import kotlinx.serialization.Serializable

@Serializable
data class GuildSettingsModel(
        val guild: GuildModel,
        val channels: List<ChannelModel>,
        val roles: List<RoleModel>,
        val modLog: Boolean,
        val modLogChannel: ChannelModel,
        val holdingRoom: Boolean,
        val holdingRoomRole: RoleModel?,
        val holdingRoomMinutes: Int,
        val inviteLinkRemover: Boolean,
        val welcomeMessage: Boolean,
        val message: String,
        val welcomeMessageChannel: ChannelModel,
        val prefix: String,
        val silentCommands: Boolean,
        val noSpacePrefix: Boolean,
        val statistics: Boolean,
        val joinCaptcha: Boolean,
        val silentCommandsLevel: Int,
        val modActionConfirmationMessage: Boolean,
        val wordFilter: Boolean,
        val wordFilterBlacklist: String?,
        val wordFilterLevel: Int,
        val wordFilterAction: Int,
        val wordFilterActionDuration: Int,
        val wordFilterActionDurationType: Int,
        val inviteLinkRemoverAction: Int,
        val inviteLinkRemoverActionDuration: Int,
        val inviteLinkRemoverActionDurationType: Int,
        val privacySettings: Int,
        val privacyModLog: Int
)