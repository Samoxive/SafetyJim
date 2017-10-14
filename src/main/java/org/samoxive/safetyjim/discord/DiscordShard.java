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
                                .setAudioEnabled(false)
                                .addEventListener(this)
                                .setReconnectQueue(new SessionReconnectQueue())
                                .setEnableShutdownHook(true)
                                .useSharding(shardId, bot.getConfig().jim.shard_count)
                                .buildBlocking();
        } catch (LoginException e) {
            System.out.println("Invalid token.");
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("Something something");
        } catch (RateLimitedException e) {
            System.out.println("Hit Discord API Rate Limit");
        }

        threadPool = Executors.newCachedThreadPool();
    }

    @Override
    public void onReady(ReadyEvent event) {
        log.info("Shard is ready.");
        // TODO(sam): Change the game text
        event.getJDA().getPresence().setGame(Game.of("-mod help"));
        DSLContext database = this.bot.getDatabase();
        for (Guild guild: event.getJDA().getGuilds()) {
            if (DiscordUtils.isBotFarm(guild)) {
                guild.leave().queue();
            }
        }

        int guildsWithMissingKeys = 0;
        for (Guild guild: event.getJDA().getGuilds()) {
            Map<String, String> guildSettings = DatabaseUtils.getGuildSettings(database, guild);

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
        Message message = event.getMessage();

        if (message.isMentioned(shard.getSelfUser()) && message.getContent().contains("prefix")) {
            String prefix = database.selectFrom(Tables.SETTINGS)
                                    .where(Tables.SETTINGS.GUILDID.eq(message.getGuild().getId()))
                                    .and(Tables.SETTINGS.KEY.eq("prefix"))
                                    .fetchOne()
                                    .getValue();
            DiscordUtils.successReact(bot, message);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setAuthor("Safety Jim - Prefix", null, shard.getSelfUser().getAvatarUrl())
                 .setDescription("This guild's prefix is: " + prefix)
                 .setColor(new Color(0x4286F4));

            message.getChannel().sendMessage(embed.build()).queue();
            return;
        }

        List<Future<Boolean>> processorResults = new LinkedList<>();
        for (MessageProcessor processor: bot.getProcessors()) {
            Future<Boolean> future = threadPool.submit(() -> processor.onMessage(bot, event));
            processorResults.add(future);
        }

        for (Future<Boolean> result: processorResults) {
            try {
                if (result.get().equals(true)) {
                    return;
                }
            } catch (Exception e) {
                //
            }
        }

        String prefix = DatabaseUtils.getGuildSetting(bot.getDatabase(), event.getGuild(), "prefix");

        String[] splitContent = message.getContent().trim().split(" ");

        if (!splitContent[0].equals(prefix)) {
            return;
        }

        if (splitContent.length == 1) {
            DiscordUtils.failReact(bot, message);
            return;
        }

        Command command = bot.getCommands().get(splitContent[1]);

        if (command == null) {
            DiscordUtils.failReact(bot, message);
            return;
        }
        StringJoiner args = new StringJoiner(" ");

        for (int i = 2; i < splitContent.length; i++) {
            args.add(splitContent[i]);
        }

        threadPool.execute(() -> executeCommand(event, command, splitContent[1], args.toString()));
    }

    @Override
    public void onException(ExceptionEvent event) {
        log.error(event.toString());
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
            processor.onReactionAdd(bot, event);
        }
    }

    @Override
    public void onGuildMessageReactionRemove(GuildMessageReactionRemoveEvent event) {
        if (event.getMember().getUser().isBot() || event.getChannelType() != ChannelType.TEXT) {
            return;
        }

        for (MessageProcessor processor: bot.getProcessors()) {
            processor.onReactionRemove(bot, event);
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        if (DiscordUtils.isBotFarm(event.getGuild())) {
            event.getGuild().leave().queue();
            return;
        }

        String message = String.format("Hello! I am Safety Jim, `%s` is my default prefix!", bot.getConfig().jim.default_prefix);
        DiscordUtils.sendMessage(DiscordUtils.getDefaultChannel(event.getGuild()), message);
        DatabaseUtils.createGuildSettings(bot, bot.getDatabase(), event.getGuild());
        bot.getMetrics().increment("guild.join");
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        bot.getDatabase().deleteFrom(Tables.SETTINGS).where(Tables.SETTINGS.GUILDID.eq(event.getGuild().getId()));
        bot.getMetrics().increment("guild.left");
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        super.onGuildMemberJoin(event);
    }

    @Override
    public void onGuildMemberLeave(GuildMemberLeaveEvent event) {
        super.onGuildMemberLeave(event);
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
