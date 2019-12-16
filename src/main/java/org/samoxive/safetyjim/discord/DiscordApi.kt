package org.samoxive.safetyjim.discord

import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import io.vertx.core.MultiMap
import io.vertx.kotlin.ext.web.client.sendAwait
import io.vertx.kotlin.ext.web.client.sendFormAwait
import java.util.concurrent.TimeUnit
import org.samoxive.safetyjim.config.Config
import org.samoxive.safetyjim.database.UserSecretsTable
import org.samoxive.safetyjim.discord.entities.DiscordPartialGuild
import org.samoxive.safetyjim.discord.entities.DiscordSelfUser
import org.samoxive.safetyjim.httpClient
import org.samoxive.safetyjim.objectMapper
import org.samoxive.safetyjim.server.models.AccessTokenResponse
import org.samoxive.safetyjim.tryhardAsync

object DiscordApi {
    private const val API_HOSTNAME = "discordapp.com"

    private val userGuildsCache: Cache<Long, List<Long>> = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build()

    suspend fun fetchSelfUser(accessToken: String): DiscordSelfUser? = tryhardAsync {
        val response = httpClient.get(443, API_HOSTNAME, "/api/users/@me")
            .putHeader("Authorization", "Bearer $accessToken")
            .sendAwait()

        objectMapper.readValue<DiscordSelfUser>(response.bodyAsString())
    }

    private suspend fun fetchSelfUserGuilds(accessToken: String): List<Long>? = tryhardAsync {
        val response = httpClient.get(443, API_HOSTNAME, "/api/users/@me/guilds")
            .putHeader("Authorization", "Bearer $accessToken")
            .sendAwait()

        objectMapper.readValue<List<DiscordPartialGuild>>(response.bodyAsString())
            .map { it.id.toLong() }
    }

    suspend fun getSelfUserGuilds(userId: Long): List<Long>? {
        val cachedGuilds = userGuildsCache.getIfPresent(userId) // not cached
        if (cachedGuilds != null) {
            return cachedGuilds
        }

        val userSecrets = UserSecretsTable.fetchUserSecrets(userId) ?: return null // not logged in before
        val guilds = fetchSelfUserGuilds(userSecrets.accessToken)
        if (guilds != null) {
            userGuildsCache.put(userId, guilds)
        }

        // if null, unauthorized by discord, if we are very unlucky, ratelimited
        return guilds
    }

    suspend fun fetchUserSecrets(config: Config, code: String): AccessTokenResponse? = tryhardAsync {
        val formBody = MultiMap.caseInsensitiveMultiMap()
        formBody.set("client_id", config.oauth.client_id)
        formBody.set("client_secret", config.oauth.client_secret)
        formBody.set("grant_type", "authorization_code")
        formBody.set("code", code)
        formBody.set("redirect_uri", config.oauth.redirect_uri)

        val response = httpClient.post(443, API_HOSTNAME, "/api/oauth2/token")
            .sendFormAwait(formBody)

        val tokenResponse = objectMapper.readValue<AccessTokenResponse>(response.bodyAsString())
        if (tokenResponse.scope != "identify guilds") {
            null
        } else {
            tokenResponse
        }
    }
}
