package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.*
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.ExceptionEvent
import net.dv8tion.jda.core.events.ReadyEvent
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
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.discord.commands.Mute
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess

class DiscordShard(private val bot: DiscordBot, shardId: Int, sessionController: SessionController) : ListenerAdapter() {
    private val log: Logger
    val shard: JDA
    val threadPool: ExecutorService = Executors.newCachedThreadPool()
    val confirmationListener = ConfirmationListener(threadPool)

    init {
        val config = bot.config
        log = LoggerFactory.getLogger("DiscordShard " + DiscordUtils.getShardString(shardId, config[JimConfig.shard_count]))

        val shardCount = config[JimConfig.shard_count]
        val version = config[JimConfig.version]

        val builder = JDABuilder(AccountType.BOT)
        this.shard = try {
            builder.setToken(config[JimConfig.token])
                    .setAudioEnabled(false) // jim doesn't have any audio functionality
                    .addEventListener(this)
                    .addEventListener(confirmationListener)
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
        shard.guilds
                .filter { guild -> getGuildSettings(guild, bot.config).statistics }
                .forEach { guild -> populateGuildStatistics(guild) }
    }

    fun populateGuildStatistics(guild: Guild) {
        val self = guild.getMember(guild.jda.selfUser)
        guild.textChannels
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
        val guild = channel.guild
        val oldestRecord = transaction {
            JimMessageTable.select {
                (JimMessageTable.guildid eq guild.id) and (JimMessageTable.channelid eq channel.id)
            }
                    .orderBy(JimMessageTable.date to SortOrder.ASC)
                    .limit(1)
                    .firstOrNull()
        }

        val newestRecord = transaction {
            JimMessageTable.select {
                (JimMessageTable.guildid eq guild.id) and (JimMessageTable.channelid eq channel.id)
            }
                    .orderBy(JimMessageTable.date to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
        }

        var fetchedMessages: List<Message>?
        if (oldestRecord == null || newestRecord == null) {
            fetchedMessages = DiscordUtils.fetchHistoryFromScratch(channel)
        } else {
            val oldestMessageStored: Message?
            val newestMessageStored: Message?

            try {
                oldestMessageStored = channel.getMessageById(oldestRecord[JimMessageTable.id].value).complete()
                newestMessageStored = channel.getMessageById(newestRecord[JimMessageTable.id].value).complete()
                if (oldestMessageStored == null || newestMessageStored == null) {
                    throw Exception()
                }

                fetchedMessages = DiscordUtils.fetchFullHistoryBeforeMessage(channel, oldestMessageStored)
                fetchedMessages.addAll(DiscordUtils.fetchFullHistoryAfterMessage(channel, newestMessageStored))
            } catch (e: Exception) {
                transaction {
                    JimMessage.find {
                        (JimMessageTable.channelid eq channel.id) and (JimMessageTable.guildid eq guild.id)
                    }.forEach { it.delete() }
                }
                fetchedMessages = DiscordUtils.fetchHistoryFromScratch(channel)
            }
        }

        if (fetchedMessages!!.isEmpty()) {
            return
        }

        transaction {
            fetchedMessages.forEach { message ->
                JimMessage.new(message.id) {
                    val user = message.author
                    val content = message.contentRaw
                    val wordCount = content.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray().size
                    userid = user.id
                    channelid = channel.id
                    guildid = channel.guild.id
                    date = DiscordUtils.getCreationTime(message.id)
                    wordcount = wordCount
                    size = content.length
                }
            }
        }
    }

    override fun onReady(event: ReadyEvent) {
        log.info("Shard is ready.")
        val shard = event.jda

        for (guild in shard.guilds) {
            if (!DiscordUtils.isGuildTalkable(guild)) {
                guild.leave().complete()
            }
        }

        threadPool.submit {
            try {
                populateStatistics(shard)
            } catch (e: Exception) {
                log.error("Failed to populate statistics!", e)
            }

            log.info("Populated statistics.")
        }
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }

        val guild = event.guild
        val message = event.message
        val content = message.contentRaw
        val shard = event.jda
        val self = shard.selfUser

        if (message.isMentioned(self) && content.contains("prefix")) {
            val guildSettings = getGuildSettings(guild, bot.config)
            val prefix = guildSettings.prefix
            DiscordUtils.successReact(bot, message)

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - Prefix", null, self.avatarUrl)
                    .setDescription("This guild's prefix is: $prefix")
                    .setColor(Color(0x4286F4))

            DiscordUtils.sendMessage(message.textChannel, embed.build())
            return
        }

        // Spread processing jobs across threads as they are likely to be independent of io operations
        val processorResults = bot.processors.map { threadPool.submit { it.onMessage(bot, this, event) } }

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

        val guildSettings = getGuildSettings(guild, bot.config)
        val prefix = guildSettings.prefix.toLowerCase()

        // 0 = prefix, 1 = command, rest are accepted as arguments
        val splitContent = content.trim().split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()
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
        threadPool.execute { executeCommand(event, command, commandName, args.toString().trim()) }
    }

    override fun onException(event: ExceptionEvent) {
        log.error("An exception occurred.", event.cause)
    }

    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        // TODO(sam): Add message cache and trigger message processors if
        // deleted message is in the cache
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
        if (event.member.user.isBot || event.channel.type != ChannelType.TEXT) {
            return
        }

        for (processor in bot.processors) {
            threadPool.execute { processor.onReactionAdd(bot, this, event) }
        }
    }

    override fun onGuildMessageReactionRemove(event: GuildMessageReactionRemoveEvent) {
        if (event.member.user.isBot || event.channel.type != ChannelType.TEXT) {
            return
        }

        for (processor in bot.processors) {
            threadPool.execute { processor.onReactionRemove(bot, this, event) }
        }
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        val guild = event.guild
        if (!DiscordUtils.isGuildTalkable(guild)) {
            guild.leave().complete()
            return
        }

        val defaultPrefix = bot.config[JimConfig.default_prefix]
        val message = "Hello! I am Safety Jim, `$defaultPrefix` is my default prefix! Try typing `$defaultPrefix help` to see available commands.\nYou can join the support server at https://discord.io/safetyjim or contact Samoxive#8634 for help."
        DiscordUtils.sendMessage(DiscordUtils.getDefaultChannel(guild), message)
        createGuildSettings(guild, bot.config)
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        deleteGuildSettings(event.guild)
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val shard = event.jda
        val guild = event.guild
        val controller = guild.controller
        val member = event.member
        val user = member.user
        val guildSettings = getGuildSettings(guild, bot.config)

        if (guildSettings.welcomemessage) {
            val textChannelId = guildSettings.welcomemessagechannelid
            val channel = shard.getTextChannelById(textChannelId)
            if (channel != null) {
                var message = guildSettings.message
                        .replace("\$user", member.asMention)
                        .replace("\$guild", guild.name)
                if (guildSettings.holdingroom) {
                    val waitTime = guildSettings.holdingroomminutes.toString()
                    message = message.replace("\$minute", waitTime)
                }

                DiscordUtils.sendMessage(channel, message)
            }
        }

        if (guildSettings.holdingroom) {
            val waitTime = guildSettings.holdingroomminutes
            val currentTime = System.currentTimeMillis() / 1000

            transaction {
                JimJoin.new {
                    userid = member.user.id
                    guildid = guild.id
                    jointime = currentTime
                    allowtime = currentTime + waitTime * 60
                    allowed = false
                }
            }
        }

        val records = transaction {
            JimMute.find {
                (JimMuteTable.guildid eq guild.id) and (JimMuteTable.userid eq user.id) and (JimMuteTable.unmuted eq false)
            }
        }

        if (transaction { records.empty() }) {
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

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) = transaction {
        JimJoin.find {
            (JimJoinTable.userid eq event.user.id) and (JimJoinTable.guildid eq event.guild.id)
        }.forEach { it.delete() }
    }

    private fun createCommandLog(event: GuildMessageReceivedEvent, commandName: String, args: String, time: Date, from: Long, to: Long) {
        val author = event.author
        transaction {
            JimCommandLog.new {
                command = commandName
                arguments = args
                this.time = DateTime(time.toInstant())
                username = DiscordUtils.getTag(author)
                userid = author.id
                guildname = event.guild.name
                guildid = event.guild.id
                executiontime = (to - from).toInt()
            }
        }
    }

    private fun executeCommand(event: GuildMessageReceivedEvent, command: Command, commandName: String, args: String) {
        val shard = event.jda

        val date = Date()
        val startTime = System.currentTimeMillis()
        var showUsage = false
        try {
            showUsage = command.run(bot, event, args)
        } catch (e: Exception) {
            DiscordUtils.failReact(bot, event.message)
            DiscordUtils.sendMessage(event.channel, "There was an error running your command, this incident has been logged.")
            log.error(String.format("%s failed with arguments %s in guild %s - %s", commandName, args, event.guild.name, event.guild.id), e)
        } finally {
            val endTime = System.currentTimeMillis()
            threadPool.submit { createCommandLog(event, commandName, args, date, startTime, endTime) }
        }

        if (showUsage) {
            val usages = command.usages
            val guildSettings = getGuildSettings(event.guild, bot.config)
            val prefix = guildSettings.prefix

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - \"$commandName\" Syntax", null, shard.selfUser.avatarUrl)
                    .setDescription(DiscordUtils.getUsageString(prefix, usages))
                    .setColor(Color(0x4286F4))

            DiscordUtils.failReact(bot, event.message)
            event.channel.sendMessage(embed.build()).queue()
        } else {
            val deleteCommands = arrayOf("ban", "kick", "mute", "softban", "warn", "hardban")

            for (deleteCommand in deleteCommands) {
                if (commandName == deleteCommand) {
                    DiscordUtils.deleteCommandMessage(bot, event.message)
                    return
                }
            }
        }
    }
}
