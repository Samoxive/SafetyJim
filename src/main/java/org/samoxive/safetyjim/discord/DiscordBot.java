package org.samoxive.safetyjim.discord;

import com.sun.javafx.scene.control.skin.VirtualFlow;
import net.dv8tion.jda.core.Permission;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DiscordBot {
    private Logger log = LoggerFactory.getLogger(DiscordBot.class);
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

        String inviteLink = shards.get(0).getShard().asBot().getInviteUrl(
                Permission.KICK_MEMBERS,
                Permission.BAN_MEMBERS,
                Permission.MESSAGE_ADD_REACTION,
                Permission.MESSAGE_READ,
                Permission.MESSAGE_WRITE,
                Permission.MESSAGE_MANAGE,
                Permission.MANAGE_ROLES
        );
        log.info("All shards ready.");
        log.info("Bot invite link: " + inviteLink);


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
