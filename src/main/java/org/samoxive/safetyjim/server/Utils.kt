package org.samoxive.safetyjim.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.vertx.core.http.HttpServerResponse
import io.vertx.kotlin.coroutines.await
import org.samoxive.safetyjim.config.Config
import org.samoxive.safetyjim.database.UUIDBlocklistTable
import org.samoxive.safetyjim.tryhardAsync
import java.time.Duration
import java.time.Instant
import java.util.*

suspend fun getUserIdFromToken(config: Config, token: String) = tryhardAsync {
    val algorithm = Algorithm.HMAC512(config.server.secret)
    val verifier = JWT.require(algorithm).build()
    val decodedToken = verifier.verify(token)

    if (decodedToken.expiresAt.before(Date())) {
        return@tryhardAsync null
    }

    val uuidString = decodedToken.getClaim("uuid").asString()
    val uuid = UUID.fromString(uuidString)
    if (UUIDBlocklistTable.isUUIDBlocklisted(uuid)) {
        return@tryhardAsync null
    }

    decodedToken.getClaim("userId").asString()
}

fun createJWTFromUserId(config: Config, userId: String): String {
    val algorithm = Algorithm.HMAC512(config.server.secret)
    val expiresAt = Date.from(Instant.now() + Duration.ofDays(7))

    return JWT.create()
        .withClaim("userId", userId)
        .withExpiresAt(expiresAt)
        .withClaim("uuid", UUID.randomUUID().toString())
        .sign(algorithm)
}

suspend fun HttpServerResponse.endJsonString(string: String) {
    putHeader("Content-Type", "application/json")
    end("\"$string\"").await()
}

suspend fun HttpServerResponse.endJson(json: String) {
    putHeader("Content-Type", "application/json")
    end(json).await()
}
