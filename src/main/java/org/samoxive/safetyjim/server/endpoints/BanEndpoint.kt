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
import org.samoxive.safetyjim.database.BanEntity
import org.samoxive.safetyjim.database.BansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.BanModel
import org.samoxive.safetyjim.server.models.toBanModel

@Serializable
data class GetBansEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<BanModel>
)

class GetBansEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val bans = BansTable.fetchGuildBans(guild, page).map { it.toBanModel(bot) }
        val pageCount = (BansTable.fetchGuildBansCount(guild) / 10) + 1
        val body = GetBansEndpointResponse(page, pageCount, bans)
        response.endJson(Json.stringify(GetBansEndpointResponse.serializer(), body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/bans"
    override val method: HttpMethod = HttpMethod.GET
}

class GetBanEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val banId = request.getParam("banId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = banId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid ban id!")
        val ban = BansTable.fetchBan(id) ?: return Result(Status.NOT_FOUND, "Ban with given id doesn't exist!")

        response.endJson(Json.stringify(BanModel.serializer(), ban.toBanModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/bans/:banId"
    override val method: HttpMethod = HttpMethod.GET
}
