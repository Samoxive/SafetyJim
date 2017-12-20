package org.samoxive.safetyjim.discord.entities;

public class User {
    public String id;
    public String username;
    public String discriminator;
    public String avatar;

    public User(String id, String username, String discriminator, String avatar) {
        this.id = id;
        this.username = username;
        this.discriminator = discriminator;
        this.avatar = avatar;
    }
}
