package org.samoxive.safetyjim.discord;

import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.*;
import net.dv8tion.jda.core.events.guild.GuildJoinEvent;
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent;
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.dv8tion.jda.core.requests.SessionReconnectQueue;
import org.jooq.DSLContext;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.CommandlogsRecord;
import org.samoxive.jooq.generated.tables.records.JoinlistRecord;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DiscordShard extends ListenerAdapter {
    private Logger log;
    private DiscordBot bot;
    private JDA shard;
    private int shardId;
    private ExecutorService threadPool;

    public DiscordShard(DiscordBot bot, int shardId) {
        this.bot = bot;
        this.shardId = shardId;
        log = LoggerFactory.getLogger("DiscordShard " + DiscordUtils.getShardString(shardId, bot.getConfig().jim.shard_count));

        JDABuilder builder = new JDABuilder(AccountType.BOT);
        try {
            this.shard = builder.setToken(bot.getConfig().jim.token)
                                .setAudioEnabled(false) // jim doesn't have any audio functionality
                                .addEventListener(this)
                                .setReconnectQueue(new SessionReconnectQueue()) // needed to prevent shards trying to reconnect too soon
                                .setEnableShutdownHook(true)
                                .useSharding(shardId, bot.getConfig().jim.shard_count)
                                .setGame(Game.of("-mod help"))
                                .buildBlocking();
        } catch (LoginException e) {
            log.error("Invalid token.");
            System.exit(1);
        } catch (InterruptedException e) {
            log.error("Something something", e);
            System.exit(1);
        } catch (RateLimitedException e) {
            log.error("Hit Discord API Rate Limit", e);
            System.exit(1);
        }

        threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public void onReady(ReadyEvent event) {
        log.info("Shard is ready.");
        DSLContext database = this.bot.getDatabase();
        for (Guild guild: shard.getGuilds()) {
            if (DiscordUtils.isBotFarm(guild)) {
                guild.leave().queue();
            }
        }

        int guildsWithMissingKeys = 0;
        for (Guild guild: shard.getGuilds()) {
            Map<String, String> guildSettings = DatabaseUtils.getGuildSettings(database, guild);

            // Guild has more or less amount of keys than the normal count so we reset them
            // This is also the case if a guild joined when jim was offline, which means their settings didn't get initialized
            if (guildSettings.size() != DatabaseUtils.possibleSettingKeys.length) {
                DatabaseUtils.deleteGuildSettings(database, guild);
                DatabaseUtils.createGuildSettings(this.bot, database, guild);
                guildsWithMissingKeys++;
            }
        }

        if (guildsWithMissingKeys > 0) {
            log.warn("Added {} guild(s) to the database with invalid number of settings.", guildsWithMissingKeys);
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        String content = message.getContent();
        SelfUser self = shard.getSelfUser();

        if (message.isMentioned(self) && content.contains("prefix")) {
            String prefix = DatabaseUtils.getGuildSetting(database, guild, "prefix");
            DiscordUtils.successReact(bot, message);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Safety Jim - Prefix", null, self.getAvatarUrl())
                 .setDescription("This guild's prefix is: " + prefix)
                 .setColor(new Color(0x4286F4));

            DiscordUtils.sendMessage(message.getTextChannel(), embed.build());
            return;
        }

        List<Future<Boolean>> processorResults = new LinkedList<>();

        // Spread processing jobs across threads as they are likely to be independent of io operations
        for (MessageProcessor processor: bot.getProcessors()) {
            Future<Boolean> future = threadPool.submit(() -> processor.onMessage(bot, event));
            processorResults.add(future);
        }

        // If processors return true, that means they deleted the original message so we don't need to continue further
        for (Future<Boolean> result: processorResults) {
            try {
                if (result.get().equals(true)) {
                    return;
                }
            } catch (Exception e) {
                //
            }
        }

        String prefix = DatabaseUtils.getGuildSetting(database, guild, "prefix");

        // 0 = prefix, 1 = command, rest are accepted as arguments
        String[] splitContent = content.trim().split(" ");

        // We want prefixes to be case insensitive
        if (!splitContent[0].toLowerCase().equals(prefix)) {
            return;
        }

        // This means the user only entered the prefix
        if (splitContent.length == 1) {
            DiscordUtils.failReact(bot, message);
            return;
        }

        // We also want commands to be case insensitive
        Command command = bot.getCommands().get(splitContent[1].toLowerCase());

        // Command not found
        if (command == null) {
            DiscordUtils.failReact(bot, message);
            return;
        }

        // Join words back with whitespace as some commands don't need them split,
        // they can split the arguments again if needed
        StringJoiner args = new StringJoiner(" ");
        for (int i = 2; i < splitContent.length; i++) {
            args.add(splitContent[i]);
        }

        // Command executions are likely to be io dependant, better send them in a seperate thread to not block
        // discord client
        threadPool.execute(() -> executeCommand(event, command, splitContent[1], args.toString()));
    }

    @Override
    public void onException(ExceptionEvent event) {
        log.error("An exception occurred.", event.getCause());
    }

    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) {
        // TODO(sam): Add message cache and trigger message processors if
        // deleted message is in the cache
    }

    @Override
    public void onGuildMessageReactionAdd(GuildMessageReactionAddEvent event) {
        if (event.getMember().getUser().isBot() || event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        for (MessageProcessor processor: bot.getProcessors()) {
            threadPool.execute(() -> processor.onReactionAdd(bot, event));
        }
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        if (event.getMember().getUser().isBot() || event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        for (MessageProcessor processor: bot.getProcessors()) {
            threadPool.execute(() -> processor.onReactionRemove(bot, event));
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        if (DiscordUtils.isBotFarm(event.getGuild())) {
            event.getGuild().leave().queue();
            return;
        }

        Guild guild = event.getGuild();
        DSLContext database = bot.getDatabase();
        String message = String.format("Hello! I am Safety Jim, `%s` is my default prefix!", bot.getConfig().jim.default_prefix);
        DiscordUtils.sendMessage(DiscordUtils.getDefaultChannel(guild), message);
        DatabaseUtils.createGuildSettings(bot, database, guild);
        bot.getMetrics().increment("guild.join");
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        DatabaseUtils.deleteGuildSettings(bot.getDatabase(), event.getGuild());
        bot.getMetrics().increment("guild.left");
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        DSLContext database = bot.getDatabase();
        Map<String, String> settings = DatabaseUtils.getGuildSettings(database, guild);

        if (settings.get("welcomemessageactive").equals("true")) {
            String textChannelId = settings.get("welcomemessagechannelid");
            TextChannel channel = shard.getTextChannelById(textChannelId);
            if (channel != null) {
                String message = settings.get("welcomemessage")
                                         .replace("$user", member.getAsMention())
                                         .replace("$guild", guild.getName());
                if (settings.get("holdingroomactive").equals("true")) {
                    String waitTime = settings.get("holdingroomminutes");
                    message = message.replace("$minute", waitTime);
                }

                DiscordUtils.sendMessage(channel, message);
            }
        }

        if (settings.get("holdingroomactive").equals("true")) {
            int waitTime = Integer.parseInt(settings.get("holdingroomminutes"));
            long currentTime = System.currentTimeMillis() / 1000;

            JoinlistRecord newRecord = database.newRecord(Tables.JOINLIST);
            newRecord.setUserid(member.getUser().getId());
            newRecord.setGuildid(guild.getId());
            newRecord.setJointime(currentTime);
            newRecord.setAllowtime(currentTime + waitTime * 60);
            newRecord.setAllowed(false);
            newRecord.store();
        }
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        bot.getDatabase()
           .deleteFrom(Tables.JOINLIST)
           .where(Tables.JOINLIST.USERID.eq(event.getUser().getId()))
           .execute();
    }

    public JDA getShard() {
        return shard;
    }

    private void createCommandLog(GuildMessageReceivedEvent event, Command command, String commandName, String args) {
        User author = event.getAuthor();
        CommandlogsRecord record = Tables.COMMANDLOGS.newRecord();
        record.setCommand(commandName);
        record.setArguments(args);
        record.setUsername(DiscordUtils.getTag(author));
        record.setUserid(author.getId());
        record.setGuildname(event.getGuild().getName());
        record.setGuildid(event.getGuild().getId());
    }

    private void executeCommand(GuildMessageReceivedEvent event, Command command, String commandName, String args) {
        createCommandLog(event, command, commandName, args);
        bot.getMetrics().increment("command.count");

        long startTime = System.currentTimeMillis();
        boolean showUsage = false;
        try {
            showUsage = command.run(bot, event, args);
        } catch (Exception e) {
            DiscordUtils.failReact(bot, event.getMessage());
            DiscordUtils.sendMessage(event.getChannel(), "There was an error running your command, this incident has been logged.");
            log.error(String.format("%s failed with arguments %s in guild %s - %s", commandName, args, event.getGuild().getName(), event.getGuild().getId()), e);
        } finally {
            long endTime = System.currentTimeMillis();
            bot.getMetrics().increment(commandName + ".count");
            bot.getMetrics().histogram(commandName + ".time", (int)(endTime - startTime));
        }

        if (showUsage) {
            String[] usages = command.getUsages();
            String prefix = DatabaseUtils.getGuildSetting(bot.getDatabase(), event.getGuild(), "prefix");

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Safety Jim - \"" + commandName + "\" Syntax", null, shard.getSelfUser().getAvatarUrl())
                 .setDescription(DiscordUtils.getUsageString(prefix, usages))
                 .setColor(new Color(0x4286F4));

            DiscordUtils.failReact(bot, event.getMessage());
            event.getChannel().sendMessage(embed.build()).queue();
        }
    }
}
