package org.samoxive.safetyjim.discord;

import com.sun.javafx.scene.control.skin.VirtualFlow;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DiscordBot {
    private List<DiscordShard> shards;
    private DSLContext database;
    private Config config;
    private HashMap<String, Command> commands;
    private HashMap<String, MessageProcessor> processors;

    public DiscordBot(DSLContext database, Config config) {
        this.database = database;
        this.config = config;
        this.shards = new ArrayList<>();
        this.commands = new HashMap<>();
        this.processors = new HashMap<>();

        for (int i = 0; i < config.jim.shard_count; i++) {
            DiscordShard shard = new DiscordShard(this, i);
            shards.add(shard);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public long getGuildCount() {
        return shards.stream()
                     .map(shard -> shard.getShard())
                     .mapToLong(shards -> shards.getGuildCache().size())
                     .sum();
    }

    private void loadCommands() {

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
