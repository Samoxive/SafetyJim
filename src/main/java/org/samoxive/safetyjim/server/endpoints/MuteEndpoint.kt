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
import org.samoxive.safetyjim.database.MutesTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.fetchMember
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.MuteModel
import org.samoxive.safetyjim.server.models.toMuteModel
import org.samoxive.safetyjim.tryhard

data class GetMutesEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<MuteModel>
)

class GetMutesEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val mutes = MutesTable.fetchGuildMutes(guild, page).map { it.toMuteModel(bot) }
        val pageCount = (MutesTable.fetchGuildMutesCount(guild) / 10) + 1
        val body = GetMutesEndpointResponse(page, pageCount, mutes)
        response.endJson(objectMapper.writeValueAsString(body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/mutes"
    override val method: HttpMethod = HttpMethod.GET
}

class GetMuteEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val muteId = request.getParam("muteId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = muteId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid mute id!")
        val mute = MutesTable.fetchMute(id) ?: return Result(Status.NOT_FOUND, "Mute with given id doesn't exist!")

        response.endJson(objectMapper.writeValueAsString(mute.toMuteModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/mutes/:muteId"
    override val method: HttpMethod = HttpMethod.GET
}

class UpdateMuteEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val muteId = request.getParam("muteId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = muteId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid mute id!")
        val mute = MutesTable.fetchMute(id) ?: return Result(Status.NOT_FOUND, "Mute with given id doesn't exist!")

        val bodyString = event.bodyAsString ?: return Result(Status.BAD_REQUEST)
        val parsedMute = tryhard { objectMapper.readValue<MuteModel>(bodyString) }
                ?: return Result(Status.BAD_REQUEST)

        val newMute = parsedMute.copy(
                reason = parsedMute.reason.trim()
        )

        if (!member.hasPermission(Permission.MANAGE_ROLES)) {
            return Result(Status.FORBIDDEN, "You don't have permissions to change mute history!")
        }

        if (mute.guildId != guild.idLong) {
            return Result(Status.FORBIDDEN, "Given mute id doesn't belong to your guild!")
        }

        if (mute.id != newMute.id ||
                mute.userId.toString() != newMute.user.id ||
                mute.muteTime != newMute.actionTime
        ) {
            return Result(Status.BAD_REQUEST, "Read only properties were modified!")
        }

        if (mute.unmuted) { // expired
            if (!newMute.unmuted || // un-expiring the mute
                    mute.expireTime != newMute.expirationTime) { // changing expiration time
                return Result(Status.BAD_REQUEST, "You can't change expiration property after user has been unmutened.")
            }
        }

        if (mute.moderatorUserId.toString() != newMute.moderatorUser.id) {
            // if original mod isn't in server that's fine, next changes should have a mod in server, next mods must be in server
            // to get permission related information
            val moderator = guild.fetchMember(newMute.moderatorUser.id) ?: return Result(Status.BAD_REQUEST, "Given moderator isn't in the guild!")
            if (!moderator.hasPermission(Permission.MANAGE_ROLES)) {
                return Result(Status.BAD_REQUEST, "Selected moderator isn't privileged enough to issue this action!")
            }
        }

        MutesTable.updateMute(mute.copy(
                moderatorUserId = newMute.moderatorUser.id.toLong(),
                expireTime = newMute.expirationTime,
                expires = newMute.expirationTime != 0L,
                unmuted = newMute.unmuted,
                reason = if (newMute.reason.isBlank()) "No reason specified" else newMute.reason
        ))

        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/mutes/:muteId"
    override val method: HttpMethod = HttpMethod.POST
}
