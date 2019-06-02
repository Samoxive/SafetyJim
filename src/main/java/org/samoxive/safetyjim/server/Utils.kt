package org.samoxive.safetyjim.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.uchuhimo.konf.Config
import io.vertx.core.http.HttpServerResponse
import io.vertx.kotlin.core.http.endAwait
import org.samoxive.safetyjim.config.ServerConfig
import org.samoxive.safetyjim.tryhard

fun getUserIdFromToken(config: Config, token: String) = tryhard {
    val algorithm = Algorithm.HMAC512(config[ServerConfig.secret])
    val verifier = JWT.require(algorithm).build()
    val decodedToken = verifier.verify(token)
    decodedToken.getClaim("userId").asString()
}

fun getJWTFromUserId(config: Config, userId: String): String {
    val algorithm = Algorithm.HMAC512(config[ServerConfig.secret])
    return JWT.create()
            .withClaim("userId", userId)
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