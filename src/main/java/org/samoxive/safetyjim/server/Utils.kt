package org.samoxive.safetyjim.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.uchuhimo.konf.Config
import io.vertx.core.http.HttpServerResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.samoxive.safetyjim.await
import org.samoxive.safetyjim.config.ServerConfig
import org.samoxive.safetyjim.tryhard
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

suspend fun Call.asyncExecute() = suspendCoroutine<Response> { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
}

fun HttpServerResponse.endJsonString(string: String) {
    putHeader("Content-Type", "application/json")
    end("\"$string\"")
}

suspend fun HttpServerResponse.endJson(json: String) = await {
    putHeader("Content-Type", "application/json")
    end(json)
}