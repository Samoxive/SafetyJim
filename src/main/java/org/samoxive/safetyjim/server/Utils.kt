package org.samoxive.safetyjim.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.uchuhimo.konf.Config
import io.vertx.core.http.HttpServerResponse
import kotlinx.serialization.json.JSON
import okhttp3.Call
import org.samoxive.safetyjim.await
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

suspend fun Call.asyncExecute() = await { execute() }
fun HttpServerResponse.endJson(string: String) {
    putHeader("Content-Type", "application/json")
    end("\"$string\"")
}

suspend inline fun <reified T: Any> HttpServerResponse.endJson(obj: T) = await {
    putHeader("Content-Type", "application/json")
    end(JSON.stringify(obj))
}