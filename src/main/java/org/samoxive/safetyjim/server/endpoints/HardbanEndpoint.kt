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
import org.samoxive.safetyjim.database.HardbansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.HardbanModel
import org.samoxive.safetyjim.server.models.toHardbanModel

@Serializable
data class GetHardbansEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<HardbanModel>
)

class GetHardbansEndpoint(bot: DiscordBot) : ModLogPaginationEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity, page: Int): Result {
        val hardbans = HardbansTable.fetchGuildHardbans(guild, page).map { it.toHardbanModel(bot) }
        val pageCount = (HardbansTable.fetchGuildHardbansCount(guild) / 10) + 1
        val body = GetHardbansEndpointResponse(page, pageCount, hardbans)
        response.endJson(Json.stringify(GetHardbansEndpointResponse.serializer(), body))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/hardbans"
    override val method: HttpMethod = HttpMethod.GET
}

class GetHardbanEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
    override suspend fun handle(event: RoutingContext, request: HttpServerRequest, response: HttpServerResponse, user: User, guild: Guild, member: Member, settings: SettingsEntity): Result {
        val hardbanId = request.getParam("hardbanId") ?: return Result(Status.SERVER_ERROR, "How did this happen?")
        val id = hardbanId.toIntOrNull() ?: return Result(Status.BAD_REQUEST, "Invalid hardban id!")
        val hardban = HardbansTable.fetchHardban(id) ?: return Result(Status.NOT_FOUND, "Hardban with given id doesn't exist!")

        response.endJson(Json.stringify(HardbanModel.serializer(), hardban.toHardbanModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/hardbans/:hardbanId"
    override val method: HttpMethod = HttpMethod.GET
}
