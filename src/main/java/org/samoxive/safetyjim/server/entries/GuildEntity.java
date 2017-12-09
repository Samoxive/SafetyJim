package org.samoxive.safetyjim.server.entries;

public class GuildEntity {
    public String id;
    public String name;
    public String avatarUrl;

    public GuildEntity(String id, String name, String avatarUrl) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
    }
}
