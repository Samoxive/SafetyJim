package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.*;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.managers.GuildController;
import net.dv8tion.jda.core.utils.SessionController;
import net.dv8tion.jda.core.utils.SessionControllerAdapter;
import okhttp3.*;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.json.JSONObject;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.Oauthsecrets;
import org.samoxive.jooq.generated.tables.records.*;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.commands.*;
import org.samoxive.safetyjim.discord.commands.Invite;
import org.samoxive.safetyjim.discord.entities.DiscordSecrets;
import org.samoxive.safetyjim.discord.entities.PartialGuild;
import org.samoxive.safetyjim.discord.processors.InviteLink;
import org.samoxive.safetyjim.discord.processors.MessageStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordBot {
    private Logger log = LoggerFactory.getLogger(DiscordBot.class);
    private List<DiscordShard> shards;
    private DSLContext database;
    private Config config;
    private HashMap<String, Command> commands;
    private List<MessageProcessor> processors;
    private ScheduledExecutorService scheduler;
    private OkHttpClient httpClient;
    private Date startTime;

    public DiscordBot(DSLContext database, Config config) {
        this.startTime = new Date();
        this.database = database;
        this.config = config;
        this.shards = new ArrayList<>();
        this.commands = new HashMap<>();
        this.processors = new ArrayList<>();
        httpClient = new OkHttpClient();
        scheduler = Executors.newScheduledThreadPool(8);

        loadCommands();
        loadProcessors();

        SessionController sessionController = new SessionControllerAdapter();
        for (int i = 0; i < config.jim.shard_count; i++) {
            DiscordShard shard = new DiscordShard(this, i, sessionController);
            shards.add(shard);

            // Discord API rate limits login requests to once per 5 seconds
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        scheduler.scheduleAtFixedRate(() -> { try { allowUsers(); } catch (Exception e) { log.error("Exception occured in allowUsers", e); } }, 10, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> { try { unmuteUsers(); } catch (Exception e) { log.error("Exception occured in unmuteUsers", e); } }, 10, 10, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> { try { unbanUsers(); } catch (Exception e) { log.error("Exception occured in unbanUsers", e); } }, 10, 30, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> { try { remindReminders(); } catch (Exception e) { log.error("Exception occured in remindReminders", e); } }, 10, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(() -> { try { saveMemberCounts(); } catch (Exception e) { log.error("Exception occured in saveMemberCounts", e); } }, 1, 10, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(() -> { try { updateBotLists(); } catch (Exception e) { log.error("Exception occured in updateBotLists", e); } }, 10, 1, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(() -> { try { updateOauthTokens(); } catch (Exception e) { log.error("Exception occured in updateOauthTokens", e); } }, 1, 30, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(() -> { try { updateGuildsOfOauthUsers(); } catch (Exception e) { log.error("Exception occured in updateGuildsOfOauthUsers", e); } }, 1, 60 * 24, TimeUnit.MINUTES);

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
        commands.put("help", new Help());
        commands.put("clean", new Clean());
        commands.put("tag", new Tag());
        commands.put("remind", new Remind());
        commands.put("info", new Info());
        commands.put("settings", new Settings());
        commands.put("softban", new Softban());
        commands.put("unban", new Unban());
        commands.put("server", new Server());
        commands.put("iam", new Iam());
        commands.put("role", new RoleCommand());
    }

    private void loadProcessors() {
        processors.add(new InviteLink());
        processors.add(new MessageStats());
    }

    private void saveMemberCounts() {
        Map<String, SettingsRecord> settings = DatabaseUtils.getAllGuildSettings(database);
        List<MembercountsRecord> records = shards.stream()
              .map((shard) -> shard.getShard().getGuilds())
              .flatMap(List::stream)
              .filter(guild -> settings.get(guild.getId()).getStatistics())
              .map(guild -> {
                  MembercountsRecord record = database.newRecord(Tables.MEMBERCOUNTS);
                  List<Member> members = guild.getMembers();
                  long onlineCount = members.stream().filter((member -> DiscordUtils.isOnline(member))).count();
                  record.setGuildid(guild.getId());
                  record.setDate((new Date()).getTime());
                  record.setOnlinecount((int)onlineCount);
                  record.setCount(members.size());
                  return record;
              })
              .collect(Collectors.toList());

        database.batchStore(records).execute();
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

            SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);
            boolean enabled = guildSettings.getHoldingroom();

            if (enabled) {
                User guildUser = DiscordUtils.getUserById(shard.getShard(), user.getUserid());
                Member member = guild.getMember(guildUser);
                String roleId = guildSettings.getHoldingroomroleid();
                Role role = guild.getRoleById(roleId);
                GuildController controller = guild.getController();

                if (role == null) {
                    guildSettings.setHoldingroom(false);
                    guildSettings.update();
                    continue;
                }

                try {
                    controller.addSingleRoleToMember(member, role).complete();
                } finally {
                    user.setAllowed(true);
                    user.update();
                }
            } else {
                user.setAllowed(true);
                user.update();
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

            User guildUser = DiscordUtils.getUserById(shard.getShard(), user.getUserid());
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

            User guildUser = DiscordUtils.getUserById(shard.getShard(), user.getUserid());
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

    private void remindReminders() {
        long now = (new Date().getTime()) / 1000;

        Result<ReminderlistRecord> reminders = database.selectFrom(Tables.REMINDERLIST)
                .where(Tables.REMINDERLIST.REMINDED.eq(false))
                .and(Tables.REMINDERLIST.REMINDTIME.lt(now))
                .fetch();

        for (ReminderlistRecord reminder: reminders) {
            String guildId = reminder.getGuildid();
            long guildIdLong = Long.parseLong(guildId);
            String channelId = reminder.getChannelid();
            String userId = reminder.getUserid();
            int shardId = DiscordUtils.getShardIdFromGuildId(guildIdLong, config.jim.shard_count);
            JDA shard = shards.get(shardId).getShard();
            Guild guild = shard.getGuildById(guildId);
            User user = DiscordUtils.getUserById(shard, userId);

            if (guild == null) {
                reminder.setReminded(true);
                reminder.update();
                continue;
            }

            TextChannel channel = guild.getTextChannelById(channelId);
            Member member = guild.getMember(user);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Reminder - #" + reminder.getId());
            embed.setDescription(reminder.getMessage());
            embed.setAuthor("Safety Jim", null, shard.getSelfUser().getAvatarUrl());
            embed.setFooter("Reminder set on", null);
            embed.setTimestamp((new Date(reminder.getRemindtime() * 1000)).toInstant());
            embed.setColor(new Color(0x4286F4));

            if (channel == null || member == null) {
                DiscordUtils.sendDM(user, embed.build());
            } else {
                try {
                    MessageBuilder builder = new MessageBuilder();
                    builder.append(user.getAsMention());
                    builder.setEmbed(embed.build());

                    channel.sendMessage(builder.build()).complete();
                } catch (Exception e) {
                    DiscordUtils.sendDM(user, embed.build());
                } finally {
                    reminder.setReminded(true);
                    reminder.update();
                }
            }
        }
    }

    public long getGuildCount() {
        return shards.stream()
                .map(shard -> shard.getShard())
                .mapToLong(shard -> shard.getGuildCache().size())
                .sum();
    }

    public void updateBotLists() {
        if (!config.botlist.enabled) {
            return;
        }

        long guildCount = getGuildCount();
        String clientId = shards.get(0).getShard().getSelfUser().getId();
        for (Config.list list: config.botlist.list) {
            JSONObject body = new JSONObject().put("server_count", guildCount);
            Request.Builder builder = new Request.Builder();
            builder.addHeader("Content-Type", "application/json")
                   .addHeader("Authorization", list.token)
                   .url(list.url.replace("$id", clientId))
                   .post(RequestBody.create(MediaType.parse("application/json"), body.toString()));
            httpClient.newCall(builder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    log.error("Failed to update a bot list.", e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (list.ignore_errors) {
                        return;
                    }

                    if (!response.isSuccessful()) {
                        log.error("Failed to update " + list.name + ".\n" + response.toString());
                    }

                    response.close();
                }
            });
        }
    }

    public void updateOauthTokens() {
        Result<OauthsecretsRecord> records = database.selectFrom(Tables.OAUTHSECRETS)
                                                     .where(Tables.OAUTHSECRETS.EXPIRATIONDATE.lt(System.currentTimeMillis() / 1000))
                                                     .fetch();

        for (OauthsecretsRecord record: records) {
            DiscordSecrets secrets = DiscordApiUtils.refreshUserSecrets(config, record.getRefreshtoken());
            if (secrets == null) {
                record.delete();
                continue;
            }

            record.setAccesstoken(secrets.accessToken);
            record.setExpirationdate((System.currentTimeMillis() / 1000) + secrets.expiresIn);
            record.update();

            try {
                Thread.sleep(1000);
            } catch (Exception e) {}
        }
    }

    public void updateGuildsOfOauthUsers() {
        Result<OauthsecretsRecord> records = database.selectFrom(Tables.OAUTHSECRETS)
                                                     .fetch();

        for (OauthsecretsRecord record: records) {
            String[] guildIds = DiscordApiUtils.getUserGuilds(record.getAccesstoken())
                                               .stream()
                                               .map((guild) -> guild.id)
                                               .toArray((size) -> new String[size]);

            record.setGuilds(guildIds);
            record.update();

            try {
                Thread.sleep(2000);
            } catch (Exception e) {}
        }
    }

    public Date getStartTime() {
        return startTime;
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

    public List<Guild> getGuilds() {
        return shards.stream()
                     .flatMap((shard) -> shard.getShard().getGuilds().stream())
                     .collect(Collectors.toList());
    }

    public DSLContext getDatabase() {
        return database;
    }

    public Config getConfig() {
        return config;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }
}
