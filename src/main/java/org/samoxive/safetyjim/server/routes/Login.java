package org.samoxive.safetyjim.server.routes;

import io.netty.handler.codec.http.HttpRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.DiscordBot;
import org.samoxive.safetyjim.server.RequestHandler;
import org.samoxive.safetyjim.server.Server;

import java.net.URLEncoder;

public class Login extends RequestHandler {
    private OkHttpClient httpClient;
    public Login(DiscordBot bot, DSLContext database, Server server, Config config) {
        super(bot, database, server, config);
        httpClient = new OkHttpClient();
    }

    private String getAuthenticationBody(String code) {
        RequestBody body = (new MultipartBody.Builder())
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("client_id", config.oauth.client_id)
                        .addFormDataPart("client_secret", config.oauth.client_secret)
                        .addFormDataPart("grant_type", "authorization_code")
                        .addFormDataPart("code", code)
                        .addFormDataPart("redirect_uri", config.oauth.redirect_uri)
                        .build();

        Request request = (new Request.Builder())
                .url("https://discordapp.com/api/oauth2/token")
                .post(body)
                .build();

        try {
            return httpClient.newCall(request)
                             .execute()
                             .body()
                             .string();
        } catch (Exception e) {
            return null;
        }
    }

    private void redirectToDiscord(HttpServerResponse response) {
        try {
            response.setStatusCode(302);
            response.putHeader("Location", "https://discordapp.com/api/oauth2/authorize?" +
                    "client_id=" + config.oauth.client_id +
                    "&redirect_uri=" + URLEncoder.encode(config.oauth.redirect_uri, "utf-8") +
                    "&response_type=code" +
                    "&scope=identify");
            response.end();
        } catch (Exception e) {

        }
    }

    @Override
    public void handle(RoutingContext ctx, Server server, DiscordBot bot, DSLContext database) {
        HttpServerRequest request = ctx.request();
        HttpServerResponse response = ctx.response();
        String code = request.getParam("code");

        if (code == null) {
            redirectToDiscord(response);
            return;
        }

        String secretsJson = getAuthenticationBody(code);
        JSONObject secretsObject = new JSONObject(secretsJson);
        String refreshToken = secretsObject.getString("refresh_token");
        String accessToken = secretsObject.getString("access_token");
        int expiresIn = secretsObject.getInt("expires_in");

        if (refreshToken == null ||
            accessToken == null ||
            expiresIn == 0) {
            redirectToDiscord(response);
            return;
        }

        

    }
}
