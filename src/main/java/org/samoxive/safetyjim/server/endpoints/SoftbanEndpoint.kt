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
import org.samoxive.safetyjim.database.SoftbansTable
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.SoftbanModel
import org.samoxive.safetyjim.server.models.toSoftbanModel

@Serializable
data class GetSoftbansEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<SoftbanModel>
)

class GetSoftbansEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val softbans = SoftbansTable.fetchGuildSoftbans(guild, page).map { it.toSoftbanModel(bot) }
        val pageCount = (SoftbansTable.fetchGuildSoftbansCount(guild) / 10) + 1
        val body = GetSoftbansEndpointResponse(page, pageCount, softbans)
        response.endJson(Json.stringify(GetSoftbansEndpointResponse.serializer(), body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/softbans"
    override val method: HttpMethod = HttpMethod.GET
}

class GetSoftbanEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val softbanId = request.getParam("softbanId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = softbanId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid softban id!")
        val softban = SoftbansTable.fetchSoftban(id) ?: return Result(Status.NOT_FOUND, "Softban with given id doesn't exist!")

        response.endJson(Json.stringify(SoftbanModel.serializer(), softban.toSoftbanModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/softbans/:softbanId"
    override val method: HttpMethod = HttpMethod.GET
}

