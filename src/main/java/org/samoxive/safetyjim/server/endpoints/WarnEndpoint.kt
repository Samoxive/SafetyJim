package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.WarnsTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.WarnModel
import org.samoxive.safetyjim.server.models.toWarnModel

@Serializable
data class GetWarnsEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<WarnModel>
)

class GetWarnsEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val warns = WarnsTable.fetchGuildWarns(guild, page).map { it.toWarnModel(bot) }
        val pageCount = (WarnsTable.fetchGuildWarnsCount(guild) / 10) + 1
        val body = GetWarnsEndpointResponse(page, pageCount, warns)
        response.endJson(Json.stringify(GetWarnsEndpointResponse.serializer(), body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/warns"
    override val method: HttpMethod = HttpMethod.GET
}

class GetWarnEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val warnId = request.getParam("warnId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = warnId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid warn id!")
        val warn = WarnsTable.fetchWarn(id) ?: return Result(Status.NOT_FOUND, "Warn with given id doesn't exist!")

        response.endJson(Json.stringify(WarnModel.serializer(), warn.toWarnModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/warns/:warnId"
    override val method: HttpMethod = HttpMethod.GET
}

