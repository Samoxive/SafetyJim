package org.samoxive.safetyjim.discord;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.entities.DiscordSecrets;
import org.samoxive.safetyjim.discord.entities.PartialGuild;
import org.samoxive.safetyjim.discord.entities.SelfUser;
import org.samoxive.safetyjim.discord.entities.User;

import java.util.ArrayList;
import java.util.List;

public class DiscordApiUtils {
    private static final HttpUrl CDN_LINK = HttpUrl.parse("https://cdn.discordapp.com/");
    private static final HttpUrl API_ENDPOINT = HttpUrl.parse("https://discordapp.com/api");
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

        HttpUrl url = API_ENDPOINT.newBuilder()
                                  .addPathSegment("oauth2")
                                  .addPathSegment("token")
                                  .build();

        Request request = (new Request.Builder())
                .url(url)
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

    public static DiscordSecrets refreshUserSecrets(Config config, String refreshToken) {
        RequestBody body = (new MultipartBody.Builder())
                .setType(MultipartBody.FORM)
                .addFormDataPart("client_id", config.oauth.client_id)
                .addFormDataPart("client_secret", config.oauth.client_secret)
                .addFormDataPart("grant_type", "refresh_token")
                .addFormDataPart("refresh_token", refreshToken)
                .addFormDataPart("redirect_uri", config.oauth.redirect_uri)
                .build();

        HttpUrl url = API_ENDPOINT.newBuilder()
                                  .addPathSegment("oauth2")
                                  .addPathSegment("token")
                                  .build();

        Request request = (new Request.Builder())
                .url(url)
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

    public static User getUser(String userId, String botToken) {
        HttpUrl url = API_ENDPOINT.newBuilder()
                                  .addPathSegment("users")
                                  .addPathSegment(userId)
                                  .build();

        Request request = (new Request.Builder())
                .url(url)
                .addHeader("Authorization", "Bot " + botToken)
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

            return gson.fromJson(userJson, User.class);
        } catch (Exception e) {
            return null;
        }

    }

    public static List<PartialGuild> getUserGuilds(String accessToken) {
        HttpUrl url = API_ENDPOINT.newBuilder()
                                  .addPathSegment("users")
                                  .addPathSegment("@me")
                                  .addPathSegment("guilds")
                                  .build();

        Request request = (new Request.Builder())
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("User-Agent", "Safety Jim")
                .get()
                .build();

        try {
            ResponseBody responseBody = client.newCall(request)
                                              .execute()
                                              .body();
            String guildsJson = responseBody.string();
            if (guildsJson == null) {
                return null;
            }

            return gson.fromJson(guildsJson, new TypeToken<ArrayList<PartialGuild>>(){}.getType());
        } catch (Exception e) {
            return null;
        }
    }

    public static String getGuildIconUrl(String id, String iconHash) {
        return CDN_LINK.newBuilder()
                       .addPathSegment("icons")
                       .addPathSegment(id)
                       .addPathSegment(iconHash + ".png")
                       .toString();
    }
}
