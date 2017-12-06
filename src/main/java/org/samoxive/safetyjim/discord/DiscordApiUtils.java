package org.samoxive.safetyjim.discord;

import com.google.gson.Gson;
import okhttp3.*;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.entities.DiscordSecrets;
import org.samoxive.safetyjim.discord.entities.SelfUser;

public class DiscordApiUtils {
    private static OkHttpClient client = new OkHttpClient();
    private static Gson gson = new Gson();

    public static DiscordSecrets getUserSecrets(Config config, String code) {
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
                .addHeader("User-Agent", "Safety Jim")
                .post(body)
                .build();

        try {
            ResponseBody responseBody = client.newCall(request)
                                              .execute()
                                              .body();
            String secretsJson = responseBody.string();
            if (secretsJson == null) {
                return null;
            }

            return gson.fromJson(secretsJson, DiscordSecrets.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static SelfUser getSelf(String accessToken) {
        Request request = (new Request.Builder())
                .url("https://discordapp.com/api/users/@me")
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("User-Agent", "Safety Jim")
                .get()
                .build();

        try {
            ResponseBody responseBody = client.newCall(request)
                                              .execute()
                                              .body();

            String userJson = responseBody.string();
            if (userJson == null) {
                return null;
            }

            return gson.fromJson(userJson, SelfUser.class);
        } catch (Exception e) {
            return null;
        }
    }
}
