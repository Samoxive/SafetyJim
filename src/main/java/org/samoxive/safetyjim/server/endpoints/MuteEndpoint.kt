package org.samoxive.safetyjim.server.endpoints

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.list
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.samoxive.safetyjim.database.MutesTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.server.ModLogEndpoint
import org.samoxive.safetyjim.server.Result
import org.samoxive.safetyjim.server.Status
import org.samoxive.safetyjim.server.endJson
import org.samoxive.safetyjim.server.models.MuteModel
import org.samoxive.safetyjim.server.models.toMuteModel

@Serializable
data class GetMutesEndpointResponse(
    val currentPage: Int,
    val totalPages: Int,
    val entries: List<MuteModel>
)

class GetMutesEndpoint(bot: DiscordBot) : ModLogEndpoint(bot) {
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