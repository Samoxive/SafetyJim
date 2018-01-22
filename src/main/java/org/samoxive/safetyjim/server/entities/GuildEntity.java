package org.samoxive.safetyjim.server.entities;

import java.util.List;

public class GuildEntity {
    public String id;
    public String name;
    public String avatarUrl;
    public List<PartialChannel> channels;

    public GuildEntity(String id, String name, String avatarUrl, List<PartialChannel> channels) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.channels = channels;
    }
}
