package org.samoxive.safetyjim.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.uchuhimo.konf.Config
import io.vertx.core.http.HttpServerResponse
import io.vertx.kotlin.core.http.endAwait
import org.samoxive.safetyjim.config.ServerConfig
import org.samoxive.safetyjim.database.UUIDBlacklistTable
import org.samoxive.safetyjim.tryhardAsync
import java.time.Duration
import java.time.Instant
import java.util.*

suspend fun getUserIdFromToken(config: Config, token: String) = tryhardAsync {
    val algorithm = Algorithm.HMAC512(config[ServerConfig.secret])
    val verifier = JWT.require(algorithm).build()
    val decodedToken = verifier.verify(token)

    if (decodedToken.expiresAt.before(Date())) {
        return@tryhardAsync null
    }

    val uuidString = decodedToken.getClaim("uuid").asString()
    val uuid = UUID.fromString(uuidString)
    if (UUIDBlacklistTable.isUUIDBlacklisted(uuid)) {
        return@tryhardAsync null
    }

    decodedToken.getClaim("userId").asString()
}

fun createJWTFromUserId(config: Config, userId: String): String {
    val algorithm = Algorithm.HMAC512(config[ServerConfig.secret])
    val expiresAt = Date.from(Instant.now() + Duration.ofDays(7))

    return JWT.create()
            .withClaim("userId", userId)
            .withExpiresAt(expiresAt)
            .withClaim("uuid", UUID.randomUUID().toString())
            .sign(algorithm)
}

suspend fun HttpServerResponse.endJsonString(string: String) {
    putHeader("Content-Type", "application/json")
    endAwait("\"$string\"")
}

suspend fun HttpServerResponse.endJson(json: String) {
    putHeader("Content-Type", "application/json")
    endAwait(json)
}
