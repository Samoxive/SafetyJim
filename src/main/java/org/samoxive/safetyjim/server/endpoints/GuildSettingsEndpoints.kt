package org.samoxive.safetyjim.server.endpoints

import com.fasterxml.jackson.module.kotlin.readValue
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SettingsTable
import org.samoxive.safetyjim.database.getDelta
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.isStaff
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.AuthenticatedGuildEndpoint
import org.samoxive.safetyjim.server.Result
import org.samoxive.safetyjim.server.Status
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.models.GuildSettingsModel
import org.samoxive.safetyjim.server.models.toChannelModel
import org.samoxive.safetyjim.server.models.toGuildModel
import org.samoxive.safetyjim.server.models.toRoleModel
import org.samoxive.safetyjim.tryhard
import org.samoxive.safetyjim.tryhardAsync

class GetGuildSettingsEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override val route = "/guilds/:guildId/settings"
    override val method = HttpMethod.GET

    private fun canMemberView(member: Member, settings: SettingsEntity): Boolean {
        return when (settings.privacySettings) {
            SettingsEntity.PRIVACY_EVERYONE -> true
            SettingsEntity.PRIVACY_STAFF_ONLY -> member.isStaff()
            SettingsEntity.PRIVACY_ADMIN_ONLY -> member.hasPermission(Permission.ADMINISTRATOR)
            else -> throw IllegalStateException()
        }
    }

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val settingsEntity = SettingsTable.getGuildSettings(guild, bot.config)

        if (!canMemberView(member, settingsEntity)) {
            return Result(Status.FORBIDDEN, "You are not allowed to view server settings!")
        }

        val holdingRoomRole = if (settingsEntity.holdingRoomRoleId != null) guild.getRoleById(settingsEntity.holdingRoomRoleId) else null
        val settings = GuildSettingsModel(
            guild.toGuildModel(),
            guild.textChannels.map { it.toChannelModel() },
            guild.roles.map { it.toRoleModel() },
            settingsEntity.modLog,
            guild.getTextChannelById(settingsEntity.modLogChannelId)?.toChannelModel()
                ?: return Result(Status.SERVER_ERROR),
            settingsEntity.holdingRoom,
            holdingRoomRole?.toRoleModel(),
            settingsEntity.holdingRoomMinutes,
            settingsEntity.inviteLinkRemover,
            settingsEntity.welcomeMessage,
            settingsEntity.message,
            guild.getTextChannelById(settingsEntity.welcomeMessageChannelId)?.toChannelModel()
                ?: return Result(Status.SERVER_ERROR),
            settingsEntity.prefix,
            settingsEntity.silentCommands,
            settingsEntity.noSpacePrefix,
            settingsEntity.statistics,
            settingsEntity.joinCaptcha,
            settingsEntity.silentCommandsLevel,
            settingsEntity.modActionConfirmationMessage,
            settingsEntity.wordFilter,
            settingsEntity.wordFilterBlacklist,
            settingsEntity.wordFilterLevel,
            settingsEntity.wordFilterAction,
            settingsEntity.wordFilterActionDuration,
            settingsEntity.wordFilterActionDurationType,
            settingsEntity.inviteLinkRemoverAction,
            settingsEntity.inviteLinkRemoverActionDuration,
            settingsEntity.inviteLinkRemoverActionDurationType,
            settingsEntity.privacySettings,
            settingsEntity.privacyModLog,
            settingsEntity.softbanThreshold,
            settingsEntity.softbanAction,
            settingsEntity.softbanActionDuration,
            settingsEntity.softbanActionDurationType,
            settingsEntity.kickThreshold,
            settingsEntity.kickAction,
            settingsEntity.kickActionDuration,
            settingsEntity.kickActionDurationType,
            settingsEntity.muteThreshold,
            settingsEntity.muteAction,
            settingsEntity.muteActionDuration,
            settingsEntity.muteActionDurationType,
            settingsEntity.warnThreshold,
            settingsEntity.warnAction,
            settingsEntity.warnActionDuration,
            settingsEntity.warnActionDurationType,
            settingsEntity.modsCanEditTags
        )

        response.endJson(objectMapper.writeValueAsString(settings))
        return Result(Status.OK)
    }
}

class PostGuildSettingsEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override val route = "/guilds/:guildId/settings"
    override val method = HttpMethod.POST

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            return Result(Status.FORBIDDEN, "You need to be an administrator to change server settings!")
        }
        val bodyString = event.bodyAsString ?: return Result(Status.BAD_REQUEST)
        val parsedSettings = tryhard { objectMapper.readValue<GuildSettingsModel>(bodyString) }
            ?: return Result(Status.BAD_REQUEST)

        val newSettings = parsedSettings.copy(
            message = parsedSettings.message.trim(),
            prefix = parsedSettings.prefix.trim(),
            wordFilterBlacklist = parsedSettings.wordFilterBlacklist?.trim()
        )

        guild.textChannels.find { it.id == newSettings.modLogChannel.id }
            ?: return Result(Status.BAD_REQUEST, "Selected moderator log channel doesn't exist!")
        guild.textChannels.find { it.id == newSettings.welcomeMessageChannel.id }
            ?: return Result(Status.BAD_REQUEST, "Selected welcome message channel doesn't exist!")

        if (newSettings.holdingRoomRole != null) {
            guild.roles.find { it.id == newSettings.holdingRoomRole.id }
                ?: return Result(Status.BAD_REQUEST, "Selected holding room role doesn't exist!")
        } else {
            if (newSettings.joinCaptcha || newSettings.holdingRoom) {
                return Result(Status.BAD_REQUEST, "You can't enable join captcha or holding room without setting a holding room role!")
            }
        }

        if (newSettings.joinCaptcha && newSettings.holdingRoom) {
            return Result(Status.BAD_REQUEST, "You can't enable both holding room and join captcha at the same time!")
        }

        if (newSettings.holdingRoomMinutes < 0) {
            return Result(Status.BAD_REQUEST, "Holding room minutes cannot be negative!")
        }

        val message = newSettings.message
        val prefix = newSettings.prefix
        if (message.isEmpty() || prefix.isEmpty()) {
            return Result(Status.BAD_REQUEST, "")
        } else {
            if (prefix.split(" ").size != 1) {
                return Result(Status.BAD_REQUEST, "Prefix can't be multiple words!")
            }

            if (prefix.length >= 1000) {
                return Result(Status.BAD_REQUEST, "Prefix can't be too long!")
            }

            if (message.length >= 1750) {
                return Result(Status.BAD_REQUEST, "Welcome message can't be too long!")
            }
        }

        if (newSettings.statistics) {
            return Result(Status.BAD_REQUEST, "Statistics option isn't open to public yet!")
        }

        if (newSettings.silentCommandsLevel != SettingsEntity.SILENT_COMMANDS_MOD_ONLY && newSettings.silentCommandsLevel != SettingsEntity.SILENT_COMMANDS_ALL) {
            return Result(Status.BAD_REQUEST, "Invalid value for silent commands level!")
        }

        if (newSettings.wordFilterBlacklist != null && newSettings.wordFilterBlacklist.length > 2000) {
            return Result(Status.BAD_REQUEST, "Word filter blacklist cannot be too long!")
        }

        if (newSettings.wordFilterLevel != SettingsEntity.WORD_FILTER_LEVEL_LOW && newSettings.wordFilterLevel != SettingsEntity.WORD_FILTER_LEVEL_HIGH) {
            return Result(Status.BAD_REQUEST, "Invalid value for word filter level!")
        }

        if (newSettings.wordFilterAction < SettingsEntity.ACTION_NOTHING || newSettings.wordFilterAction > SettingsEntity.ACTION_HARDBAN) {
            return Result(Status.BAD_REQUEST, "Invalid value for word filter action!")
        }

        if (newSettings.wordFilterActionDurationType < SettingsEntity.DURATION_TYPE_SECONDS || newSettings.wordFilterActionDurationType > SettingsEntity.DURATION_TYPE_DAYS) {
            return Result(Status.BAD_REQUEST, "Invalid value for word filter action duration type!")
        }

        if (newSettings.inviteLinkRemoverAction < SettingsEntity.ACTION_NOTHING || newSettings.inviteLinkRemoverAction > SettingsEntity.ACTION_HARDBAN) {
            return Result(Status.BAD_REQUEST, "Invalid value for invite link remover action!")
        }

        if (newSettings.inviteLinkRemoverActionDurationType < SettingsEntity.DURATION_TYPE_SECONDS || newSettings.inviteLinkRemoverActionDurationType > SettingsEntity.DURATION_TYPE_DAYS) {
            return Result(Status.BAD_REQUEST, "Invalid value for invite link remover action duration type!")
        }

        if (newSettings.softbanActionDurationType < SettingsEntity.DURATION_TYPE_SECONDS || newSettings.softbanActionDurationType > SettingsEntity.DURATION_TYPE_DAYS) {
            return Result(Status.BAD_REQUEST, "Invalid value for softban action duration type!")
        }

        if (newSettings.kickActionDurationType < SettingsEntity.DURATION_TYPE_SECONDS || newSettings.kickActionDurationType > SettingsEntity.DURATION_TYPE_DAYS) {
            return Result(Status.BAD_REQUEST, "Invalid value for kick action duration type!")
        }

        if (newSettings.muteActionDurationType < SettingsEntity.DURATION_TYPE_SECONDS || newSettings.muteActionDurationType > SettingsEntity.DURATION_TYPE_DAYS) {
            return Result(Status.BAD_REQUEST, "Invalid value for mute action duration type!")
        }

        if (newSettings.warnActionDurationType < SettingsEntity.DURATION_TYPE_SECONDS || newSettings.warnActionDurationType > SettingsEntity.DURATION_TYPE_DAYS) {
            return Result(Status.BAD_REQUEST, "Invalid value for warn action duration type!")
        }

        try {
            getDelta(newSettings.wordFilterActionDurationType, newSettings.wordFilterActionDuration)
            getDelta(newSettings.inviteLinkRemoverActionDurationType, newSettings.inviteLinkRemoverActionDuration)
            getDelta(newSettings.softbanActionDurationType, newSettings.softbanActionDuration)
            getDelta(newSettings.kickActionDurationType, newSettings.kickActionDuration)
            getDelta(newSettings.muteActionDurationType, newSettings.muteActionDuration)
            getDelta(newSettings.warnActionDurationType, newSettings.warnActionDuration)
        } catch (e: IllegalArgumentException) {
            return Result(Status.BAD_REQUEST, e.message!!)
        }

        if (newSettings.privacySettings < SettingsEntity.PRIVACY_EVERYONE || newSettings.privacySettings > SettingsEntity.PRIVACY_ADMIN_ONLY) {
            return Result(Status.BAD_REQUEST, "Invalid value for settings privacy!")
        }

        if (newSettings.privacyModLog < SettingsEntity.PRIVACY_EVERYONE || newSettings.privacyModLog > SettingsEntity.PRIVACY_ADMIN_ONLY) {
            return Result(Status.BAD_REQUEST, "Invalid value for moderator log privacy!")
        }

        if (newSettings.softbanAction < SettingsEntity.ACTION_NOTHING || newSettings.softbanAction > SettingsEntity.ACTION_HARDBAN) {
            return Result(Status.BAD_REQUEST, "Invalid value for softban action!")
        }

        if (newSettings.kickAction < SettingsEntity.ACTION_NOTHING || newSettings.kickAction > SettingsEntity.ACTION_HARDBAN) {
            return Result(Status.BAD_REQUEST, "Invalid value for kick action!")
        }

        if (newSettings.muteAction < SettingsEntity.ACTION_NOTHING || newSettings.muteAction > SettingsEntity.ACTION_HARDBAN) {
            return Result(Status.BAD_REQUEST, "Invalid value for mute action!")
        }

        if (newSettings.warnAction < SettingsEntity.ACTION_NOTHING || newSettings.warnAction > SettingsEntity.ACTION_HARDBAN) {
            return Result(Status.BAD_REQUEST, "Invalid value for warn action!")
        }

        if (newSettings.softbanThreshold < 0) {
            return Result(Status.BAD_REQUEST, "Softban threshold cannot be negative!")
        }

        if (newSettings.kickThreshold < 0) {
            return Result(Status.BAD_REQUEST, "Kick threshold cannot be negative!")
        }

        if (newSettings.muteThreshold < 0) {
            return Result(Status.BAD_REQUEST, "Mute threshold cannot be negative!")
        }

        if (newSettings.warnThreshold < 0) {
            return Result(Status.BAD_REQUEST, "Warn threshold cannot be negative!")
        }

        if (newSettings.guild.id != guild.id) {
            return Result(Status.BAD_REQUEST)
        }

        val wordFilterBlacklist = if (newSettings.wordFilterBlacklist != null) {
            if (newSettings.wordFilterBlacklist.isEmpty()) {
                null
            } else {
                newSettings.wordFilterBlacklist
            }
        } else {
            null
        }

        tryhardAsync {
            SettingsTable.updateSettings(
                SettingsEntity(
                    guildId = guild.idLong,
                    modLog = newSettings.modLog,
                    modLogChannelId = newSettings.modLogChannel.id.toLong(),
                    holdingRoom = newSettings.holdingRoom,
                    holdingRoomRoleId = newSettings.holdingRoomRole?.id?.toLong(),
                    holdingRoomMinutes = newSettings.holdingRoomMinutes,
                    inviteLinkRemover = newSettings.inviteLinkRemover,
                    welcomeMessage = newSettings.welcomeMessage,
                    message = newSettings.message,
                    welcomeMessageChannelId = newSettings.welcomeMessageChannel.id.toLong(),
                    prefix = newSettings.prefix,
                    silentCommands = newSettings.silentCommands,
                    noSpacePrefix = newSettings.noSpacePrefix,
                    statistics = newSettings.statistics,
                    joinCaptcha = newSettings.joinCaptcha,
                    silentCommandsLevel = newSettings.silentCommandsLevel,
                    modActionConfirmationMessage = newSettings.modActionConfirmationMessage,
                    wordFilter = newSettings.wordFilter,
                    wordFilterBlacklist = wordFilterBlacklist,
                    wordFilterLevel = newSettings.wordFilterLevel,
                    wordFilterAction = newSettings.wordFilterAction,
                    wordFilterActionDuration = newSettings.wordFilterActionDuration,
                    wordFilterActionDurationType = newSettings.wordFilterActionDurationType,
                    inviteLinkRemoverAction = newSettings.inviteLinkRemoverAction,
                    inviteLinkRemoverActionDuration = newSettings.inviteLinkRemoverActionDuration,
                    inviteLinkRemoverActionDurationType = newSettings.inviteLinkRemoverActionDurationType,
                    privacySettings = newSettings.privacySettings,
                    privacyModLog = newSettings.privacyModLog,
                    softbanThreshold = newSettings.softbanThreshold,
                    softbanAction = newSettings.softbanAction,
                    softbanActionDuration = newSettings.softbanActionDuration,
                    softbanActionDurationType = newSettings.softbanActionDurationType,
                    kickThreshold = newSettings.kickThreshold,
                    kickAction = newSettings.kickAction,
                    kickActionDuration = newSettings.kickActionDuration,
                    kickActionDurationType = newSettings.kickActionDurationType,
                    muteThreshold = newSettings.muteThreshold,
                    muteAction = newSettings.muteAction,
                    muteActionDuration = newSettings.muteActionDuration,
                    muteActionDurationType = newSettings.muteActionDurationType,
                    warnThreshold = newSettings.warnThreshold,
                    warnAction = newSettings.warnAction,
                    warnActionDuration = newSettings.warnActionDuration,
                    warnActionDurationType = newSettings.warnActionDurationType,
                    modsCanEditTags = newSettings.modsCanEditTags
                )
            )
        } ?: return Result(Status.SERVER_ERROR)

        response.end()
        return Result(Status.OK)
    }
}

class ResetGuildSettingsEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override val route = "/guilds/:guildId/settings"
    override val method = HttpMethod.DELETE

    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        if (!member.hasPermission(Permission.ADMINISTRATOR)) {
            return Result(Status.FORBIDDEN)
        }

        SettingsTable.resetSettings(guild, bot.config)
        response.end()
        return Result(Status.OK)
    }
}
