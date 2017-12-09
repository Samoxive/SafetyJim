package org.samoxive.safetyjim.server;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.vertx.core.http.HttpServerResponse;
import org.samoxive.safetyjim.config.Config;

import java.net.URLEncoder;

public class ServerUtils {
    public static String getIdFromToken(Config config, String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC512(config.server.secret);
            JWTVerifier verifier = JWT.require(algorithm)
                                      .build();
            DecodedJWT decodedJWT = verifier.verify(token);
            return decodedJWT.getClaim("userId").asString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void redirectToDiscord(HttpServerResponse response, Config config) {
        try {
            response.setStatusCode(302);
            response.putHeader("Location", "https://discordapp.com/api/oauth2/authorize?" +
                    "client_id=" + config.oauth.client_id +
                    "&redirect_uri=" + URLEncoder.encode(config.oauth.redirect_uri, "utf-8") +
                    "&response_type=code" +
                    "&scope=guilds%20identify");
            response.putHeader("User-Agent", "Safety Jim");
            response.end();
        } catch (Exception e) {

        }
    }

    public static void redirectToWebsite(HttpServerResponse response) {
        response.setStatusCode(302);
        response.putHeader("Location", "http://localhost.com");
        response.end();
    }
}
