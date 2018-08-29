package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.*
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.*
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionRemoveEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import net.dv8tion.jda.core.utils.SessionController
import org.samoxive.jooq.generated.Tables
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.DatabaseUtils
import org.samoxive.safetyjim.discord.commands.Mute
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.security.auth.login.LoginException
import java.awt.*
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.exitProcess

class DiscordShard(private val bot: DiscordBot, shardId: Int, sessionController: SessionController) : ListenerAdapter() {
    private val log: Logger
    val shard: JDA
    val threadPool: ExecutorService

    init {
        val config = bot.config
        log = LoggerFactory.getLogger("DiscordShard " + DiscordUtils.getShardString(shardId, config[JimConfig.shard_count]))

        val shardCount = config[JimConfig.shard_count]
        val version = config[JimConfig.version]

        threadPool = Executors.newCachedThreadPool()
        val builder = JDABuilder(AccountType.BOT)
        this.shard = try {
             builder.setToken(config[JimConfig.token])
                    .setAudioEnabled(false) // jim doesn't have any audio functionality
                    .addEventListener(this)
                    .setSessionController(sessionController) // needed to prevent shards trying to reconnect too soon
                    .setEnableShutdownHook(true)
                    .useSharding(shardId, config[JimConfig.shard_count])
                    .setGame(Game.playing(String.format("-mod help | %s | %s", version, DiscordUtils.getShardString(shardId, shardCount))))
                    .build()
                    .awaitReady()
        } catch (e: LoginException) {
            log.error("Invalid token.", e)
            exitProcess(1)
        } catch (e: InterruptedException) {
            log.error("Something something", e)
            exitProcess(1)
        }

    }

    private fun populateStatistics(shard: JDA) {
        val database = bot.database
        shard.guilds
                .stream()
                .filter { guild -> DatabaseUtils.getGuildSettings(bot, database, guild).statistics }
                .forEach { guild -> populateGuildStatistics(guild) }
    }

    fun populateGuildStatistics(guild: Guild) {
        val self = guild.getMember(guild.jda.selfUser)
        guild.textChannels
                .stream()
                .filter { channel -> self.hasPermission(channel, Permission.MESSAGE_HISTORY, Permission.MESSAGE_READ) }
                .map { channel -> threadPool.submit { populateChannelStatistics(channel) } }
                .forEach { future ->
                    try {
                        future.get()
                    } catch (e: Exception) {
                    }
                }
    }

    private fun populateChannelStatistics(channel: TextChannel) {
        val database = bot.database
        val guild = channel.guild
        val oldestRecord = database.selectFrom(Tables.MESSAGES)
                .where(Tables.MESSAGES.GUILDID.eq(guild.id))
                .and(Tables.MESSAGES.CHANNELID.eq(channel.id))
                .orderBy(Tables.MESSAGES.DATE.asc())
                .limit(1)
                .fetchAny()

        val newestRecord = database.selectFrom(Tables.MESSAGES)
                .where(Tables.MESSAGES.GUILDID.eq(guild.id))
                .and(Tables.MESSAGES.CHANNELID.eq(channel.id))
                .orderBy(Tables.MESSAGES.DATE.desc())
                .limit(1)
                .fetchAny()

        var fetchedMessages: List<Message>?
        if (oldestRecord == null || newestRecord == null) {
            fetchedMessages = DiscordUtils.fetchHistoryFromScratch(channel)
        } else {
            var oldestMessageStored: Message?
            var newestMessageStored: Message?

            try {
                oldestMessageStored = channel.getMessageById(oldestRecord.messageid).complete()
                newestMessageStored = channel.getMessageById(newestRecord.messageid).complete()
                if (oldestMessageStored == null || newestMessageStored == null) {
                    throw Exception()
                }

                fetchedMessages = DiscordUtils.fetchFullHistoryBeforeMessage(channel, oldestMessageStored)
                fetchedMessages.addAll(DiscordUtils.fetchFullHistoryAfterMessage(channel, newestMessageStored))
            } catch (e: Exception) {
                database.deleteFrom(Tables.MESSAGES)
                        .where(Tables.MESSAGES.CHANNELID.eq(channel.id))
                        .and(Tables.MESSAGES.GUILDID.eq(guild.id))
                        .execute()
                fetchedMessages = DiscordUtils.fetchHistoryFromScratch(channel)
            }

        }

        if (fetchedMessages!!.size == 0) {
            return
        }

        val records = fetchedMessages
                .map { message ->
                    val record = database.newRecord(Tables.MESSAGES)
                    val user = message.author
                    val content = message.contentRaw
                    val wordCount = content.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size
                    record.messageid = message.id
                    record.userid = user.id
                    record.channelid = channel.id
                    record.guildid = channel.guild.id
                    record.date = DiscordUtils.getCreationTime(message.id)
                    record.wordcount = wordCount
                    record.size = content.length
                    record
                }

        database.batchStore(records).execute()
    }

    override fun onReady(event: ReadyEvent?) {
        log.info("Shard is ready.")
        val database = this.bot.database
        val shard = event!!.jda

        for (guild in shard.guilds) {
            if (!DiscordUtils.isGuildTalkable(guild)) {
                guild.leave().complete()
            }
        }

        var guildsWithMissingKeys = 0
        for (guild in shard.guilds) {
            val guildSettings = DatabaseUtils.getGuildSettings(bot, database, guild)

            if (guildSettings == null) {
                DatabaseUtils.deleteGuildSettings(database, guild)
                DatabaseUtils.createGuildSettings(this.bot, database, guild)
                guildsWithMissingKeys++
            }
        }

        if (guildsWithMissingKeys > 0) {
            log.warn("Added {} guild(s) to the database with invalid number of settings.", guildsWithMissingKeys)
        }

        threadPool.submit {
            try {
                populateStatistics(shard)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            log.info("Populated statistics.")
        }
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent?) {
        if (event!!.author.isBot) {
            return
        }

        val database = bot.database
        val guild = event.guild
        val message = event.message
        val content = message.contentRaw
        val shard = event.jda
        val self = shard.selfUser

        if (message.isMentioned(self) && content.contains("prefix")) {
            val guildSettings = DatabaseUtils.getGuildSettings(bot, database, guild)
            val prefix = guildSettings.prefix
            DiscordUtils.successReact(bot, message)

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - Prefix", null, self.avatarUrl)
                    .setDescription("This guild's prefix is: $prefix")
                    .setColor(Color(0x4286F4))

            DiscordUtils.sendMessage(message.textChannel, embed.build())
            return
        }

        val processorResults = LinkedList<Future<Boolean>>()

        // Spread processing jobs across threads as they are likely to be independent of io operations
        for (processor in bot.processors) {
            val future = threadPool.submit<Boolean> { processor.onMessage(bot, this, event) }
            processorResults.add(future)
        }

        // If processors return true, that means they deleted the original message so we don't need to continue further
        for (result in processorResults) {
            try {
                if (result.get() == true) {
                    return
                }
            } catch (e: Exception) {
                //
            }

        }

        val guildSettings = DatabaseUtils.getGuildSettings(bot, database, guild)
                ?: // settings aren't initialized yet
                return
        val prefix = guildSettings.prefix.toLowerCase()

        // 0 = prefix, 1 = command, rest are accepted as arguments
        val splitContent = content.trim { it <= ' ' }.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val firstWord = splitContent[0].toLowerCase()
        val command: Command?
        val commandName: String

        if (!guildSettings.nospaceprefix) {
            if (firstWord != prefix) {
                return
            }

            // This means the user only entered the prefix
            if (splitContent.size == 1) {
                DiscordUtils.failReact(bot, message)
                return
            }

            // We also want commands to be case insensitive
            commandName = splitContent[1].toLowerCase()
            command = bot.commands[commandName]
        } else {
            if (!firstWord.startsWith(prefix)) {
                return
            }

            if (firstWord.length == prefix.length) {
                DiscordUtils.failReact(bot, message)
                return
            }

            commandName = firstWord.substring(prefix.length)
            command = bot.commands[commandName]
        }

        // Command not found
        if (command == null) {
            DiscordUtils.failReact(bot, message)
            return
        }

        // Join words back with whitespace as some commands don't need them split,
        // they can split the arguments again if needed
        val args = StringJoiner(" ")
        val startIndex = if (guildSettings.nospaceprefix) 1 else 2
        for (i in startIndex until splitContent.size) {
            args.add(splitContent[i])
        }

        // Command executions are likely to be io dependant, better send them in a seperate thread to not block
        // discord client
        threadPool.execute { executeCommand(event, command, commandName, args.toString().trim { it <= ' ' }) }
    }

    override fun onException(event: ExceptionEvent?) {
        log.error("An exception occurred.", event!!.cause)
    }

    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent?) {
        // TODO(sam): Add message cache and trigger message processors if
        // deleted message is in the cache
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent?) {
        if (event!!.member.user.isBot || event.channel.type != ChannelType.TEXT) {
            return
        }

        for (processor in bot.processors) {
            threadPool.execute { processor.onReactionAdd(bot, this, event) }
        }
    }

    override fun onGuildMessageReactionRemove(event: GuildMessageReactionRemoveEvent?) {
        if (event!!.member.user.isBot || event.channel.type != ChannelType.TEXT) {
            return
        }

        for (processor in bot.processors) {
            threadPool.execute { processor.onReactionRemove(bot, this, event) }
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent?) {
        val guild = event!!.guild
        if (!DiscordUtils.isGuildTalkable(guild)) {
            guild.leave().complete()
            return
        }

        val database = bot.database
        val defaultPrefix = bot.config[JimConfig.default_prefix]
        val message = String.format("Hello! I am Safety Jim, `%s` is my default prefix! Try typing `%s help` to see available commands.\n" + "You can join the support server at https://discord.io/safetyjim or contact Samoxive#8634 for help.", defaultPrefix, defaultPrefix)
        DiscordUtils.sendMessage(DiscordUtils.getDefaultChannel(guild), message)
        DatabaseUtils.createGuildSettings(bot, database, guild)
    }

    override fun onGuildLeave(event: GuildLeaveEvent?) {
        DatabaseUtils.deleteGuildSettings(bot.database, event!!.guild)
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent?) {
        val shard = event!!.jda
        val guild = event.guild
        val controller = guild.controller
        val member = event.member
        val user = member.user
        val database = bot.database
        val guildSettings = DatabaseUtils.getGuildSettings(bot, database, guild)

        if (guildSettings.welcomemessage!!) {
            val textChannelId = guildSettings.welcomemessagechannelid
            val channel = shard.getTextChannelById(textChannelId)
            if (channel != null) {
                var message = guildSettings.message
                        .replace("\$user", member.asMention)
                        .replace("\$guild", guild.name)
                if (guildSettings.holdingroom!!) {
                    val waitTime = guildSettings.holdingroomminutes!!.toString()
                    message = message.replace("\$minute", waitTime)
                }

                DiscordUtils.sendMessage(channel, message)
            }
        }

        if (guildSettings.holdingroom!!) {
            val waitTime = guildSettings.holdingroomminutes!!
            val currentTime = System.currentTimeMillis() / 1000

            val newRecord = database.newRecord(Tables.JOINLIST)
            newRecord.userid = member.user.id
            newRecord.guildid = guild.id
            newRecord.jointime = currentTime
            newRecord.allowtime = currentTime + waitTime * 60
            newRecord.allowed = false
            newRecord.store()
        }


        val records = database.selectFrom(Tables.MUTELIST)
                .where(Tables.MUTELIST.GUILDID.eq(guild.id))
                .and(Tables.MUTELIST.USERID.eq(user.id))
                .and(Tables.MUTELIST.UNMUTED.eq(false))
                .fetch()

        if (records.isEmpty()) {
            return
        }

        val mutedRole: Role = try {
            Mute.setupMutedRole(guild)
        } catch (e: Exception) {
            return
        }

        try {
            controller.addSingleRoleToMember(member, mutedRole).complete()
        } catch (e: Exception) {
            // Maybe actually do something if this fails?
        }

    }

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent?) {
        bot.database
                .deleteFrom(Tables.JOINLIST)
                .where(Tables.JOINLIST.USERID.eq(event!!.user.id))
                .execute()
    }

    private fun createCommandLog(event: GuildMessageReceivedEvent, commandName: String, args: String, time: Date, from: Long, to: Long) {
        val author = event.author
        val record = bot.database.newRecord(Tables.COMMANDLOGS)
        record.command = commandName
        record.arguments = args
        record.time = Timestamp(time.time)
        record.username = DiscordUtils.getTag(author)
        record.userid = author.id
        record.guildname = event.guild.name
        record.guildid = event.guild.id
        record.executiontime = (to - from).toInt()
        record.store()
    }

    private fun executeCommand(event: GuildMessageReceivedEvent, command: Command?, commandName: String, args: String) {
        val shard = event.jda

        val date = Date()
        val startTime = System.currentTimeMillis()
        var showUsage = false
        try {
            showUsage = command!!.run(bot, event, args)
        } catch (e: Exception) {
            DiscordUtils.failReact(bot, event.message)
            DiscordUtils.sendMessage(event.channel, "There was an error running your command, this incident has been logged.")
            log.error(String.format("%s failed with arguments %s in guild %s - %s", commandName, args, event.guild.name, event.guild.id), e)
        } finally {
            val endTime = System.currentTimeMillis()
            threadPool.submit { createCommandLog(event, commandName, args, date, startTime, endTime) }
        }

        if (showUsage) {
            val usages = command!!.usages
            val guildSettings = DatabaseUtils.getGuildSettings(bot, bot.database, event.guild)
            val prefix = guildSettings.prefix

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - \"$commandName\" Syntax", null, shard.selfUser.avatarUrl)
                    .setDescription(DiscordUtils.getUsageString(prefix, usages))
                    .setColor(Color(0x4286F4))

            DiscordUtils.failReact(bot, event.message)
            event.channel.sendMessage(embed.build()).queue()
        } else {
            val deleteCommands = arrayOf("ban", "kick", "mute", "softban", "warn")

            for (deleteCommand in deleteCommands) {
                if (commandName == deleteCommand) {
                    DiscordUtils.deleteCommandMessage(bot, event.message)
                    return
                }
            }
        }
    }
}
