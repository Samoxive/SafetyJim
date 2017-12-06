package org.samoxive.safetyjim.discord.entities;

import com.google.gson.annotations.SerializedName;

public class DiscordSecrets {
    @SerializedName("access_token")
    public String accessToken;
    @SerializedName("expires_in")
    public int expiresIn;
    @SerializedName("refresh_token")
    public String refreshToken;
    public String scope;
}
