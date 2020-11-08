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
import org.samoxive.safetyjim.database.KicksTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.KickModel
import org.samoxive.safetyjim.server.models.toKickModel
import org.samoxive.safetyjim.tryhard

data class GetKicksEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<KickModel>
)

class GetKicksEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val kicks = KicksTable.fetchGuildKicks(guild, page).map { it.toKickModel(bot) }
        val pageCount = (KicksTable.fetchGuildKicksCount(guild) / 10) + 1
        val body = GetKicksEndpointResponse(page, pageCount, kicks)
        response.endJson(objectMapper.writeValueAsString(body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/kicks"
    override val method: HttpMethod = HttpMethod.GET
}

class GetKickEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val kickId = request.getParam("kickId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = kickId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid kick id!")
        val kick = KicksTable.fetchKick(id) ?: return Result(Status.NOT_FOUND, "Kick with given id doesn't exist!")

        response.endJson(objectMapper.writeValueAsString(kick.toKickModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/kicks/:kickId"
    override val method: HttpMethod = HttpMethod.GET
}

class UpdateKickEndpoint(bot: DiscordBot) : AuthenticatedGuildEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member): Result {
        val kickId = request.getParam("kickId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = kickId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid kick id!")
        val kick = KicksTable.fetchKick(id) ?: return Result(Status.NOT_FOUND, "Kick with given id doesn't exist!")

        val bodyString = event.bodyAsString ?: return Result(Status.BAD_REQUEST)
        val parsedKick = tryhard { objectMapper.readValue<KickModel>(bodyString) }
            ?: return Result(Status.BAD_REQUEST)

        val newKick = parsedKick.copy(
            reason = parsedKick.reason.trim()
        )

        if (!member.hasPermission(Permission.KICK_MEMBERS)) {
            return Result(Status.FORBIDDEN, "You don't have permissions to change kick history!")
        }

        if (kick.guildId != guild.idLong) {
            return Result(Status.FORBIDDEN, "Given kick id doesn't belong to your guild!")
        }

        if (kick.id != newKick.id ||
            kick.userId.toString() != newKick.user.id ||
            kick.kickTime != newKick.actionTime ||
            kick.moderatorUserId.toString() != newKick.moderatorUser.id
        ) {
            return Result(Status.BAD_REQUEST, "Read only properties were modified!")
        }

        if (kick.pardoned && !newKick.pardoned) {
            return Result(Status.BAD_REQUEST, "You can't un-pardon a kick!")
        }

        KicksTable.updateKick(
            kick.copy(
                reason = if (newKick.reason.isBlank()) "No reason specified" else newKick.reason,
                pardoned = newKick.pardoned
            )
        )

        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/kicks/:kickId"
    override val method: HttpMethod = HttpMethod.POST
}
