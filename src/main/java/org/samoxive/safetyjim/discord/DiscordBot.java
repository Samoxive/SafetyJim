package org.samoxive.safetyjim.discord;

import org.jooq.DSLContext;

import java.util.List;

public class DiscordBot {
    private List<DiscordShard> shards;
    private DSLContext database;

    public DiscordBot(DSLContext database) {
        this.database = database;
    }
}
