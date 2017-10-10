package org.samoxive.safetyjim.discord;

import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;

import java.util.List;

public class DiscordBot {
    private List<DiscordShard> shards;
    private DSLContext database;
    private Config config;

    public DiscordBot(DSLContext database, Config config) {
        this.database = database;
        this.config = config;

        for (int i = 0; i < config.jim.shard_count; i++) {
            DiscordShard shard = new DiscordShard(this, i);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public List<DiscordShard> getShards() {
        return shards;
    }

    public DSLContext getDatabase() {
        return database;
    }

    public Config getConfig() {
        return config;
    }
}
