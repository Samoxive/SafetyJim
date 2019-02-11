package org.samoxive.safetyjim.discord

import com.mashape.unirest.http.Unirest
import com.uchuhimo.konf.Config
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import org.samoxive.safetyjim.awaitAsString
import org.samoxive.safetyjim.config.OauthConfig
import org.samoxive.safetyjim.discord.entities.DiscordSelfUser
import org.samoxive.safetyjim.server.asyncExecute
import org.samoxive.safetyjim.server.entities.AccessTokenResponse
import org.samoxive.safetyjim.tryhardAsync

object DiscordApi {
    private const val API_ENDPOINT = "https://discordapp.com/api"
    private val okHttpClient = OkHttpClient()

    suspend fun getSelfUser(accessToken: String): DiscordSelfUser? = tryhardAsync {
        val response = Unirest.get("$API_ENDPOINT/users/@me")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", "Safety Jim")
                .awaitAsString()

        Json.nonstrict.parse(DiscordSelfUser.serializer(), response.body)
    }

    suspend fun getUserSecrets(config: Config, code: String): AccessTokenResponse? = tryhardAsync {
        val formBody = (MultipartBody.Builder())
                .setType(MultipartBody.FORM)
                .addFormDataPart("client_id", config[OauthConfig.client_id])
                .addFormDataPart("client_secret", config[OauthConfig.client_secret])
                .addFormDataPart("grant_type", "authorization_code")
                .addFormDataPart("code", code)
                .addFormDataPart("redirect_uri", config[OauthConfig.redirect_uri])
                .build()

        val request = okhttp3.Request.Builder()
                .url("$API_ENDPOINT/oauth2/token")
                .addHeader("User-Agent", "Safety Jim")
                .post(formBody)
                .build()

        val responseBody = okHttpClient.newCall(request)
                .asyncExecute()
                .body()

        val responseString = responseBody?.string()
        if (responseString == null) {
            responseBody?.close()
            return@tryhardAsync null
        }

        val tokenResponse = Json.parse(AccessTokenResponse.serializer(), responseString)
        if (tokenResponse.scope != "identify") {
            null
        } else {
            tokenResponse
        }
    }
}