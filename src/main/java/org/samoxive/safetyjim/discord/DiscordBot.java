package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.Permission;
import org.jooq.DSLContext;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.discord.commands.Ping;
import org.samoxive.safetyjim.discord.processors.InviteLink;
import org.samoxive.safetyjim.metrics.Metrics;
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
    private List<MessageProcessor> processors;
    private Metrics metrics;

    public DiscordBot(DSLContext database, Config config, Metrics metrics) {
        this.database = database;
        this.config = config;
        this.metrics = metrics;
        this.shards = new ArrayList<>();
        this.commands = new HashMap<>();
        this.processors = new ArrayList<>();

        loadCommands();
        loadProcessors();

        for (int i = 0; i < config.jim.shard_count; i++) {
            DiscordShard shard = new DiscordShard(this, i);
            shards.add(shard);

            // Discord API rate limits login requests to once per 5 seconds
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

    private void loadCommands() {
        commands.put("ping", new Ping());
    }

    private void loadProcessors() {
        processors.add(new InviteLink());
    }

    public long getGuildCount() {
        return shards.stream()
                .map(shard -> shard.getShard())
                .mapToLong(shard -> shard.getGuildCache().size())
                .sum();
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public HashMap<String, Command> getCommands() {
        return commands;
    }

    public List<MessageProcessor> getProcessors() {
        return processors;
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
