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
import net.dv8tion.jda.core.managers.GuildController;
import net.dv8tion.jda.core.requests.SessionReconnectQueue;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.records.*;
import org.samoxive.safetyjim.config.Config;
import org.samoxive.safetyjim.database.DatabaseUtils;
import org.samoxive.safetyjim.discord.commands.Mute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class DiscordShard extends ListenerAdapter {
    private Logger log;
    private DiscordBot bot;
    private JDA shard;
    private ExecutorService threadPool;

    public DiscordShard(DiscordBot bot, int shardId) {
        this.bot = bot;
        log = LoggerFactory.getLogger("DiscordShard " + DiscordUtils.getShardString(shardId, bot.getConfig().jim.shard_count));

        Config config = bot.getConfig();
        int shardCount = config.jim.shard_count;
        String version = config.version;

        threadPool = Executors.newCachedThreadPool();
        JDABuilder builder = new JDABuilder(AccountType.BOT);
        try {
            this.shard = builder.setToken(bot.getConfig().jim.token)
                                .setAudioEnabled(false) // jim doesn't have any audio functionality
                                .addEventListener(this)
                                .setReconnectQueue(new SessionReconnectQueue()) // needed to prevent shards trying to reconnect too soon
                                .setEnableShutdownHook(true)
                                .useSharding(shardId, bot.getConfig().jim.shard_count)
                                .setGame(Game.playing(String.format("-mod help | %s | %s", version, DiscordUtils.getShardString(shardId, shardCount))))
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
    }

    private void populateStatistics(JDA shard) {
        DSLContext database = bot.getDatabase();
        shard.getGuilds()
             .stream()
             .filter((guild) -> DatabaseUtils.getGuildSettings(database, guild).getStatistics())
             .forEach((guild -> populateGuildStatistics(guild)));
    }

    public void populateGuildStatistics(Guild guild) {
        DSLContext database = bot.getDatabase();
        List<TextChannel> channels = guild.getTextChannels();
        for(TextChannel channel: channels) {
            MessagesRecord oldestRecord = database.selectFrom(Tables.MESSAGES)
                    .where(Tables.MESSAGES.GUILDID.eq(guild.getId()))
                    .and(Tables.MESSAGES.CHANNELID.eq(channel.getId()))
                    .orderBy(Tables.MESSAGES.DATE.asc())
                    .limit(1)
                    .fetchAny();

            MessagesRecord newestRecord = database.selectFrom(Tables.MESSAGES)
                    .where(Tables.MESSAGES.GUILDID.eq(guild.getId()))
                    .and(Tables.MESSAGES.CHANNELID.eq(channel.getId()))
                    .orderBy(Tables.MESSAGES.DATE.desc())
                    .limit(1)
                    .fetchAny();

            List<Message> fetchedMessages = null;
            if (oldestRecord == null || newestRecord == null) {
                fetchedMessages = DiscordUtils.fetchHistoryFromScratch(channel);
            } else {
                Message oldestMessageStored = null, newestMessageStored = null;

                try {
                    oldestMessageStored = channel.getMessageById(oldestRecord.getMessageid()).complete();
                    newestMessageStored = channel.getMessageById(newestRecord.getMessageid()).complete();
                    if (oldestMessageStored == null || newestMessageStored == null) {
                        throw new Exception();
                    }

                    fetchedMessages = DiscordUtils.fetchFullHistoryBeforeMessage(channel, oldestMessageStored);
                    fetchedMessages.addAll(DiscordUtils.fetchFullHistoryAfterMessage(channel, newestMessageStored));
                } catch (Exception e) {
                    database.deleteFrom(Tables.MESSAGES)
                            .where(Tables.MESSAGES.CHANNELID.eq(channel.getId()))
                            .and(Tables.MESSAGES.GUILDID.eq(guild.getId()))
                            .execute();
                    fetchedMessages = DiscordUtils.fetchHistoryFromScratch(channel);
                }
            }

            if (fetchedMessages.size() == 0) {
                continue;
            }

            List<MessagesRecord> records = fetchedMessages.stream()
                    .map(message -> {
                        MessagesRecord record = database.newRecord(Tables.MESSAGES);
                        User user = message.getAuthor();
                        String content = message.getRawContent();
                        int wordCount = content.split(" ").length;
                        record.setMessageid(message.getId());
                        record.setUserid(user.getId());
                        record.setChannelid(channel.getId());
                        record.setGuildid(channel.getGuild().getId());
                        record.setDate(DiscordUtils.getCreationTime(message.getId()));
                        record.setWordcount(wordCount);
                        record.setSize(content.length());
                        return record;
                    })
                    .collect(Collectors.toList());

            database.batchStore(records).execute();
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        log.info("Shard is ready.");
        DSLContext database = this.bot.getDatabase();
        JDA shard = event.getJDA();

        for (Guild guild: shard.getGuilds()) {
            if (DiscordUtils.isBotFarm(guild)) {
                guild.leave().queue();
            }
        }

        int guildsWithMissingKeys = 0;
        for (Guild guild: shard.getGuilds()) {
            SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);

            if (guildSettings == null) {
                DatabaseUtils.deleteGuildSettings(database, guild);
                DatabaseUtils.createGuildSettings(this.bot, database, guild);
                guildsWithMissingKeys++;
            }
        }

        if (guildsWithMissingKeys > 0) {
            log.warn("Added {} guild(s) to the database with invalid number of settings.", guildsWithMissingKeys);
        }

        threadPool.submit(() -> {
            try {
                populateStatistics(shard);
            } catch (Exception e) {
                e.printStackTrace();
            }
            log.info("Populated statistics.");
        });
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        DSLContext database = bot.getDatabase();
        Guild guild = event.getGuild();
        Message message = event.getMessage();
        String content = message.getRawContent();
        JDA shard = event.getJDA();
        SelfUser self = shard.getSelfUser();

        if (message.isMentioned(self) && content.contains("prefix")) {
            SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);
            String prefix = guildSettings.getPrefix();
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
            Future<Boolean> future = threadPool.submit(() -> processor.onMessage(bot, this, event));
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

        SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);
        if (guildSettings == null) { // settings aren't initialized yet
            return;
        }
        String prefix = guildSettings.getPrefix().toLowerCase();

        // 0 = prefix, 1 = command, rest are accepted as arguments
        String[] splitContent = content.trim().split(" ");
        String firstWord = splitContent[0].toLowerCase();
        Command command;
        String commandName;

        if (!guildSettings.getNospaceprefix()) {
            if (!firstWord.equals(prefix)) {
                return;
            }

            // This means the user only entered the prefix
            if (splitContent.length == 1) {
                DiscordUtils.failReact(bot, message);
                return;
            }

            // We also want commands to be case insensitive
            commandName = splitContent[1].toLowerCase();
            command = bot.getCommands().get(commandName);
        } else {
            if (!firstWord.startsWith(prefix)) {
                return;
            }

            if (firstWord.length() == prefix.length()) {
                DiscordUtils.failReact(bot, message);
                return;
            }

            commandName = firstWord.substring(prefix.length());
            command = bot.getCommands().get(commandName);
        }

        // Command not found
        if (command == null) {
            DiscordUtils.failReact(bot, message);
            return;
        }

        // Join words back with whitespace as some commands don't need them split,
        // they can split the arguments again if needed
        StringJoiner args = new StringJoiner(" ");
        int startIndex = guildSettings.getNospaceprefix() ? 1 : 2;
        for (int i = startIndex; i < splitContent.length; i++) {
            args.add(splitContent[i]);
        }

        // Command executions are likely to be io dependant, better send them in a seperate thread to not block
        // discord client
        threadPool.execute(() -> executeCommand(event, command, commandName, args.toString().trim()));
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
            threadPool.execute(() -> processor.onReactionAdd(bot, this, event));
        }
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        if (event.getMember().getUser().isBot() || event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        for (MessageProcessor processor: bot.getProcessors()) {
            threadPool.execute(() -> processor.onReactionRemove(bot, this, event));
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
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        DatabaseUtils.deleteGuildSettings(bot.getDatabase(), event.getGuild());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        JDA shard = event.getJDA();
        Guild guild = event.getGuild();
        GuildController controller = guild.getController();
        Member member = event.getMember();
        User user = member.getUser();
        DSLContext database = bot.getDatabase();
        SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(database, guild);

        if (guildSettings.getWelcomemessage()) {
            String textChannelId = guildSettings.getWelcomemessagechannelid();
            TextChannel channel = shard.getTextChannelById(textChannelId);
            if (channel != null) {
                String message = guildSettings.getMessage()
                                         .replace("$user", member.getAsMention())
                                         .replace("$guild", guild.getName());
                if (guildSettings.getHoldingroom()) {
                    String waitTime = guildSettings.getHoldingroomminutes().toString();
                    message = message.replace("$minute", waitTime);
                }

                DiscordUtils.sendMessage(channel, message);
            }
        }

        if (guildSettings.getHoldingroom()) {
            int waitTime = guildSettings.getHoldingroomminutes();
            long currentTime = System.currentTimeMillis() / 1000;

            JoinlistRecord newRecord = database.newRecord(Tables.JOINLIST);
            newRecord.setUserid(member.getUser().getId());
            newRecord.setGuildid(guild.getId());
            newRecord.setJointime(currentTime);
            newRecord.setAllowtime(currentTime + waitTime * 60);
            newRecord.setAllowed(false);
            newRecord.store();
        }


        Result<MutelistRecord> records = database.selectFrom(Tables.MUTELIST)
                                               .where(Tables.MUTELIST.GUILDID.eq(guild.getId()))
                                               .and(Tables.MUTELIST.USERID.eq(user.getId()))
                                               .fetch();

        if (records.isEmpty()) {
            return;
        }

        Role mutedRole = null;
        try {
            mutedRole = Mute.setupMutedRole(guild);
        } catch (Exception e) {
            return;
        }

        try {
            controller.addSingleRoleToMember(member, mutedRole).complete();
        } catch (Exception e) {
            // Maybe actually do something if this fails?
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

    private void createCommandLog(GuildMessageReceivedEvent event, String commandName, String args, Date time, long from, long to) {
        User author = event.getAuthor();
        CommandlogsRecord record = bot.getDatabase().newRecord(Tables.COMMANDLOGS);
        record.setCommand(commandName);
        record.setArguments(args);
        record.setTime(new Timestamp(time.getTime()));
        record.setUsername(DiscordUtils.getTag(author));
        record.setUserid(author.getId());
        record.setGuildname(event.getGuild().getName());
        record.setGuildid(event.getGuild().getId());
        record.setExecutiontime((int) (to - from));
        record.store();
    }

    private void executeCommand(GuildMessageReceivedEvent event, Command command, String commandName, String args) {
        JDA shard = event.getJDA();

        Date date = new Date();
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
            threadPool.submit(() -> createCommandLog(event, commandName, args, date, startTime, endTime));
        }

        if (showUsage) {
            String[] usages = command.getUsages();
            SettingsRecord guildSettings = DatabaseUtils.getGuildSettings(bot.getDatabase(), event.getGuild());
            String prefix = guildSettings.getPrefix();

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Safety Jim - \"" + commandName + "\" Syntax", null, shard.getSelfUser().getAvatarUrl())
                 .setDescription(DiscordUtils.getUsageString(prefix, usages))
                 .setColor(new Color(0x4286F4));

            DiscordUtils.failReact(bot, event.getMessage());
            event.getChannel().sendMessage(embed.build()).queue();
        } else {
            String[] deleteCommands = {
                    "ban", "kick", "mute", "softban", "warn"
            };

            for (String deleteCommand: deleteCommands) {
                if (commandName.equals(deleteCommand)) {
                    DiscordUtils.deleteCommandMessage(bot, event.getMessage());
                    return;
                }
            }
        }
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }
}
