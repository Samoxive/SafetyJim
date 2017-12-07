package org.samoxive.safetyjim.server;

import io.vertx.core.http.HttpServerResponse;
import org.samoxive.safetyjim.config.Config;

import java.net.URLEncoder;

public class ServerUtils {
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
