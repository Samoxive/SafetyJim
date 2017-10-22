package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.managers.GuildController;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.BanlistRecord;
import org.samoxive.jooq.generated.tables.records.JoinlistRecord;
import org.samoxive.jooq.generated.tables.records.MutelistRecord;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.commands.*;
import org.samoxive.safetyjim.discord.processors.InviteLink;
import org.samoxive.safetyjim.metrics.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordBot {
    private Logger log = LoggerFactory.getLogger(DiscordBot.class);
    private List<DiscordShard> shards;
    private DSLContext database;
    private Config config;
    private HashMap<String, Command> commands;
    private List<MessageProcessor> processors;
    private Metrics metrics;
    private ScheduledExecutorService scheduler;

    public DiscordBot(DSLContext database, Config config, Metrics metrics) {
        this.database = database;
        this.config = config;
        this.metrics = metrics;
        this.shards = new ArrayList<>();
        this.commands = new HashMap<>();
        this.processors = new ArrayList<>();
        scheduler = Executors.newScheduledThreadPool(3);

        scheduler.scheduleAtFixedRate(() -> allowUsers(), 30, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> unmuteUsers(), 30, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> unbanUsers(), 30, 30, TimeUnit.SECONDS);

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
        commands.put("unmute", new Unmute());
        commands.put("invite", new Invite());
        commands.put("ban", new Ban());
        commands.put("kick", new Kick());
        commands.put("mute", new Mute());
        commands.put("warn", new Warn());
    }

    private void loadProcessors() {
        processors.add(new InviteLink());
    }

    private void allowUsers() {
        long currentTime = System.currentTimeMillis() / 1000;

        Result<JoinlistRecord> usersToBeAllowed = database.selectFrom(Tables.JOINLIST)
                                                        .where(Tables.JOINLIST.ALLOWED.eq(false))
                                                        .and(Tables.JOINLIST.ALLOWTIME.lt(currentTime))
                                                        .fetch();

        for (JoinlistRecord user: usersToBeAllowed) {
            String guildId = user.getGuildid();
            long guildIdLong = Long.parseLong(guildId);
            int shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config.jim.shard_count);
            DiscordShard shard = shards.get(shardId);
            JDA shardClient = shard.getShard();
            Guild guild = shardClient.getGuildById(guildId);

            if (guild == null) {
                user.setAllowed(true);
                user.update();
                continue;
            }

            String enabled = DatabaseUtils.getGuildSetting(database, guild, "holdingroomactive");

            if (enabled.equals("true")) {
                User guildUser = shardClient.getUserById(user.getId());
                Member member = guild.getMember(guildUser);
                String roleId = DatabaseUtils.getGuildSetting(database, guild, "holdingroomroleid");
                Role role = guild.getRoleById(roleId);
                GuildController controller = guild.getController();

                try {
                    controller.addSingleRoleToMember(member, role).complete();
                } finally {
                    user.setAllowed(true);
                    user.update();
                }
            }
        }
    }

    private void unbanUsers() {
        long currentTime = System.currentTimeMillis() / 1000;

        Result<BanlistRecord> usersToBeUnbanned = database.selectFrom(Tables.BANLIST)
                                                          .where(Tables.BANLIST.UNBANNED.eq(false))
                                                          .and(Tables.BANLIST.EXPIRES.eq(true))
                                                          .and(Tables.BANLIST.EXPIRETIME.lt(currentTime))
                                                          .fetch();

        for (BanlistRecord user: usersToBeUnbanned) {
            String guildId = user.getGuildid();
            long guildIdLong = Long.parseLong(guildId);
            int shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config.jim.shard_count);
            DiscordShard shard = shards.get(shardId);
            JDA shardClient = shard.getShard();
            Guild guild = shardClient.getGuildById(guildId);

            if (guild == null) {
                user.setUnbanned(true);
                user.update();
                continue;
            }

            User guildUser = shardClient.getUserById(user.getId());
            GuildController controller = guild.getController();

            try {
                controller.unban(guildUser).complete();
            } finally {
                user.setUnbanned(true);
                user.update();
            }
        }
    }

    private void unmuteUsers() {
        long currentTime = System.currentTimeMillis() / 1000;

        Result<MutelistRecord> usersToBeUnmuted = database.selectFrom(Tables.MUTELIST)
                                                          .where(Tables.MUTELIST.UNMUTED.eq(false))
                                                          .and(Tables.MUTELIST.EXPIRES.eq(true))
                                                          .and(Tables.MUTELIST.EXPIRETIME.lt(currentTime))
                                                          .fetch();

        for (MutelistRecord user: usersToBeUnmuted) {
            String guildId = user.getGuildid();
            long guildIdLong = Long.parseLong(guildId);
            int shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config.jim.shard_count);
            DiscordShard shard = shards.get(shardId);
            JDA shardClient = shard.getShard();
            Guild guild = shardClient.getGuildById(guildId);

            if (guild == null) {
                user.setUnmuted(true);
                user.update();
                continue;
            }

            User guildUser = shardClient.getUserById(user.getId());
            Member member = guild.getMember(guildUser);
            Role role = guild.getRolesByName("Muted", false).get(0);
            GuildController controller = guild.getController();

            try {
                controller.removeSingleRoleFromMember(member, role).complete();
            } finally {
                user.setUnmuted(true);
                user.update();
            }
        }
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
