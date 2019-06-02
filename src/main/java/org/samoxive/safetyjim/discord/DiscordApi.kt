package org.samoxive.safetyjim.discord

import com.uchuhimo.konf.Config
import io.vertx.core.MultiMap
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.sendFormAwait
import kotlinx.serialization.json.Json
import org.samoxive.safetyjim.config.OauthConfig
import org.samoxive.safetyjim.discord.entities.DiscordSelfUser
import org.samoxive.safetyjim.httpClient
import org.samoxive.safetyjim.server.entities.AccessTokenResponse
import org.samoxive.safetyjim.tryhardAsync

object DiscordApi {
    private const val API_HOSTNAME = "discordapp.com"

    suspend fun getSelfUser(accessToken: String): DiscordSelfUser? = tryhardAsync {
        val response = httpClient.get(443, API_HOSTNAME, "/api/users/@me")
                .putHeader("Authorization", "Bearer $accessToken")
                .sendAwait()

        Json.nonstrict.parse(DiscordSelfUser.serializer(), response.bodyAsString())
    }

    suspend fun getUserSecrets(config: Config, code: String): AccessTokenResponse? = tryhardAsync {
        val formBody = MultiMap.caseInsensitiveMultiMap()
        formBody.set("client_id", config[OauthConfig.client_id])
        formBody.set("client_secret", config[OauthConfig.client_secret])
        formBody.set("grant_type", "authorization_code")
        formBody.set("code", code)
        formBody.set("redirect_uri", config[OauthConfig.redirect_uri])

        val response = httpClient.post(443, API_HOSTNAME, "/api/oauth2/token")
                .sendFormAwait(formBody)

        val tokenResponse = Json.parse(AccessTokenResponse.serializer(), response.bodyAsString())
        if (tokenResponse.scope != "identify") {
            null
        } else {
            tokenResponse
        }
    }
}