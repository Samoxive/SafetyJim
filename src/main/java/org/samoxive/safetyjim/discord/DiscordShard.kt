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
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.config.ServerConfig
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.discord.commands.Mute
import org.samoxive.safetyjim.discord.processors.isInviteLinkBlacklisted
import org.samoxive.safetyjim.tryhard
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess

class DiscordShard(private val bot: DiscordBot, shardId: Int, sessionController: SessionController) : ListenerAdapter() {
    private val log: Logger
    val jda: JDA
    val threadPool: ExecutorService = Executors.newCachedThreadPool()
    val confirmationListener = ConfirmationListener(threadPool)

    init {
        val config = bot.config
        log = LoggerFactory.getLogger("DiscordShard " + getShardString(shardId, config[JimConfig.shard_count]))

        val shardCount = config[JimConfig.shard_count]
        val version = config[JimConfig.version]

        val builder = JDABuilder(AccountType.BOT)
        this.jda = try {
            builder.setToken(config[JimConfig.token])
                    .setAudioEnabled(false) // jim doesn't have any audio functionality
                    .addEventListener(this)
                    .addEventListener(confirmationListener)
                    .setSessionController(sessionController) // needed to prevent shards trying to reconnect too soon
                    .setEnableShutdownHook(true)
                    .useSharding(shardId, config[JimConfig.shard_count])
                    .setGame(Game.playing("-mod help | $version | ${getShardString(shardId, shardCount)}"))
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

    private fun populateGuildStatistics(guild: Guild) {
        val self = guild.getMember(guild.jda.selfUser)
        guild.textChannels
                .asSequence()
                .filter { channel -> self.hasPermission(channel, Permission.MESSAGE_HISTORY, Permission.MESSAGE_READ) }
                .map { channel -> threadPool.submit { populateChannelStatistics(channel) } }
                .toList()
                .forEach { future -> tryhard { future.get() } }
    }

    private fun populateChannelStatistics(channel: TextChannel) {
        val guild = channel.guild
        val oldestRecord = transaction {
            JimMessageTable.select {
                (JimMessageTable.guildid eq guild.idLong) and (JimMessageTable.channelid eq channel.idLong)
            }
                    .orderBy(JimMessageTable.date to SortOrder.ASC)
                    .limit(1)
                    .firstOrNull()
        }

        val newestRecord = transaction {
            JimMessageTable.select {
                (JimMessageTable.guildid eq guild.idLong) and (JimMessageTable.channelid eq channel.idLong)
            }
                    .orderBy(JimMessageTable.date to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
        }

        var fetchedMessages: List<Message>?
        if (oldestRecord == null || newestRecord == null) {
            fetchedMessages = channel.fetchHistoryFromScratch()
        } else {
            val oldestMessageStored: Message?
            val newestMessageStored: Message?

            try {
                oldestMessageStored = channel.getMessageById(oldestRecord[JimMessageTable.id].value).complete()
                newestMessageStored = channel.getMessageById(newestRecord[JimMessageTable.id].value).complete()
                if (oldestMessageStored == null || newestMessageStored == null) {
                    throw Exception()
                }

                fetchedMessages = channel.fetchFullHistoryBeforeMessage(oldestMessageStored)
                fetchedMessages.addAll(channel.fetchFullHistoryAfterMessage(newestMessageStored))
            } catch (e: Exception) {
                transaction {
                    JimMessage.find {
                        (JimMessageTable.channelid eq channel.idLong) and (JimMessageTable.guildid eq guild.idLong)
                    }.forEach { it.delete() }
                }
                fetchedMessages = channel.fetchHistoryFromScratch()
            }
        }

        if (fetchedMessages!!.isEmpty()) {
            return
        }

        transaction {
            fetchedMessages.forEach { message ->
                JimMessage.new(message.idLong) {
                    val user = message.author
                    val content = message.contentRaw
                    val wordCount = content.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray().size
                    userid = user.idLong
                    channelid = channel.idLong
                    guildid = channel.guild.idLong
                    date = message.id.getCreationTime()
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
            if (!guild.isTalkable()) {
                guild.leave().complete()
                continue
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
            message.successReact(bot)

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - Prefix", null, self.avatarUrl)
                    .setDescription("This guild's prefix is: $prefix")
                    .setColor(Color(0x4286F4))

            message.textChannel.trySendMessage(embed.build())
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
        val splitContent = content.trim().split(" ").toTypedArray()
        val firstWord = splitContent[0].toLowerCase()
        val command: Command?
        val commandName: String

        if (!guildSettings.nospaceprefix) {
            if (firstWord != prefix) {
                return
            }

            // This means the user only entered the prefix
            if (splitContent.size == 1) {
                message.failReact(bot)
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
                message.failReact(bot)
                return
            }

            commandName = firstWord.substring(prefix.length)
            command = bot.commands[commandName]
        }

        // Command not found
        if (command == null) {
            message.failReact(bot)
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
        if (!guild.isTalkable()) {
            guild.leave().complete()
            return
        }

        val defaultPrefix = bot.config[JimConfig.default_prefix]
        val message = "Hello! I am Safety Jim, `$defaultPrefix` is my default prefix! Try typing `$defaultPrefix help` to see available commands.\nYou can join the support server at https://discord.io/safetyjim or contact Samoxive#8634 for help."
        guild.getDefaultChannelTalkable().trySendMessage(message)
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

        if (guildSettings.invitelinkremover) {
            if (isInviteLinkBlacklisted(user.name)) {
                tryhard { controller.kick(member).complete() }
                return
            }
        }

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

                channel.trySendMessage(message)
            }
        }

        if (guildSettings.holdingroom) {
            val waitTime = guildSettings.holdingroomminutes
            val currentTime = System.currentTimeMillis() / 1000

            transaction {
                JimJoin.new {
                    userid = member.user.idLong
                    guildid = guild.idLong
                    jointime = currentTime
                    allowtime = currentTime + waitTime * 60
                    allowed = false
                }
            }
        }

        if (guildSettings.joincaptcha) {
            val captchaUrl = "${bot.config[ServerConfig.self_url]}captcha/${guild.id}/${user.id}/"
            user.sendMessage("Welcome to ${guild.name}! To enter you must complete this captcha.\n$captchaUrl")
        }

        val records = transaction {
            JimMute.find {
                (JimMuteTable.guildid eq guild.idLong) and (JimMuteTable.userid eq user.idLong) and (JimMuteTable.unmuted eq false)
            }
        }

        if (transaction { records.empty() }) {
            return
        }

        val mutedRole: Role = tryhard { Mute.setupMutedRole(guild) } ?: return
        tryhard { controller.addSingleRoleToMember(member, mutedRole).complete() }
    }

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) = transaction {
        JimJoin.find {
            (JimJoinTable.userid eq event.user.idLong) and (JimJoinTable.guildid eq event.guild.idLong)
        }.forEach { it.delete() }
    }

    private fun executeCommand(event: GuildMessageReceivedEvent, command: Command, commandName: String, args: String) {
        val shard = event.jda
        val message = event.message
        val channel = event.channel

        var showUsage = false
        try {
            showUsage = command.run(bot, event, args)
        } catch (e: Exception) {
            message.failReact(bot)
            channel.trySendMessage("There was an error running your command, this incident has been logged.")
            log.error(String.format("%s failed with arguments %s in guild %s - %s", commandName, args, event.guild.name, event.guild.id), e)
        }

        if (showUsage) {
            val usages = command.usages
            val guildSettings = getGuildSettings(event.guild, bot.config)
            val prefix = guildSettings.prefix

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - \"$commandName\" Syntax", null, shard.selfUser.avatarUrl)
                    .setDescription(getUsageString(prefix, usages))
                    .setColor(Color(0x4286F4))

            message.failReact(bot)
            channel.trySendMessage(embed.build())
        } else {
            for (deleteCommand in bot.deleteCommands) {
                if (commandName == deleteCommand) {
                    message.deleteCommandMessage(bot)
                    return
                }
            }
        }
    }
}
