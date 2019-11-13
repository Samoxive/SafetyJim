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
import org.samoxive.safetyjim.database.MutesTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.*
import org.samoxive.safetyjim.server.models.MuteModel
import org.samoxive.safetyjim.server.models.toMuteModel

@Serializable
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
        response.endJson(Json.stringify(GetMutesEndpointResponse.serializer(), body))
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

        response.endJson(Json.stringify(MuteModel.serializer(), mute.toMuteModel(bot)))
        return Result(Status.OK)
    }

    override val route: String = "/guilds/:guildId/mutes/:muteId"
    override val method: HttpMethod = HttpMethod.GET
}
