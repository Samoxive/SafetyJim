package org.samoxive.safetyjim.discord

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.Role
import net.dv8tion.jda.core.events.ExceptionEvent
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.guild.GuildJoinEvent
import net.dv8tion.jda.core.events.guild.GuildLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import net.dv8tion.jda.core.utils.SessionController
import net.dv8tion.jda.core.utils.cache.CacheFlag
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.config.ServerConfig
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.database.SettingsTable.getGuildSettings
import org.samoxive.safetyjim.discord.commands.Mute
import org.samoxive.safetyjim.discord.processors.isInviteLinkBlacklisted
import org.samoxive.safetyjim.tryhardAsync
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess

class DiscordShard(private val bot: DiscordBot, shardId: Int, sessionController: SessionController) : ListenerAdapter() {
    private val log: Logger
    val jda: JDA
    val confirmationListener = ConfirmationListener()

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
                    .setDisabledCacheFlags(EnumSet.of(CacheFlag.EMOTE, CacheFlag.GAME, CacheFlag.VOICE_STATE))
                    .setGame(Game.playing("patreon.com/safetyjim | -mod help"))
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

    override fun onReady(event: ReadyEvent) {
        log.info("Shard is ready.")

        GlobalScope.launch {
            onReadyAsync(event)
        }
    }

    private suspend fun onReadyAsync(event: ReadyEvent) {
        val shard = event.jda

        for (guild in shard.guilds) {
            if (guild.textChannels.isEmpty()) {
                tryhardAsync { guild.leave().await() }
                continue
            }
        }
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        GlobalScope.launch { onGuildMessageReceivedAsync(event) }
    }

    private suspend fun onGuildMessageReceivedAsync(event: GuildMessageReceivedEvent) {
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
        val processorResult = bot.processors.map {
            GlobalScope.async {
                tryhardAsync {
                    it.onMessage(bot, this@DiscordShard, event)
                } ?: false
            }
        }.map { it.await() }.reduce { acc, elem -> acc || elem }

        // If processors return true, that means they deleted the original message so we don't need to continue further
        if (processorResult) {
            return
        }

        val guildSettings = getGuildSettings(guild, bot.config)
        val prefix = guildSettings.prefix.toLowerCase()

        // 0 = prefix, 1 = command, rest are accepted as arguments
        val splitContent = content.trim().split(" ").toTypedArray()
        val firstWord = splitContent[0].toLowerCase()
        val command: Command?
        val commandName: String

        if (!guildSettings.noSpacePrefix) {
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
        val startIndex = if (guildSettings.noSpacePrefix) 1 else 2
        for (i in startIndex until splitContent.size) {
            args.add(splitContent[i])
        }

        // Command executions are likely to be io dependant, better send them in a seperate thread to not block
        // discord client
        executeCommand(event, guildSettings, command, commandName, args.toString().trim())
    }

    override fun onGuildMessageDelete(event: GuildMessageDeleteEvent) {
        bot.processors.forEach { processor -> GlobalScope.launch { processor.onMessageDelete(bot, this@DiscordShard, event) } }
    }

    override fun onException(event: ExceptionEvent) {
        log.error("An exception occurred.", event.cause)
    }

    override fun onGuildJoin(event: GuildJoinEvent) {
        GlobalScope.launch {
            val guild = event.guild
            if (guild.textChannels.isEmpty()) {
                guild.leave().await()
                return@launch
            }

            val defaultPrefix = bot.config[JimConfig.default_prefix]
            val message = "Hello! I am Safety Jim, `$defaultPrefix` is my default prefix! Visit https://safetyjim.xyz/commands to see available commands.\nYou can join the support server at https://discord.io/safetyjim or contact Samoxive#8634 for help."
            guild.getDefaultChannelTalkable().trySendMessage(message)
            SettingsTable.insertDefaultGuildSettings(bot.config, guild)
        }
    }

    override fun onGuildLeave(event: GuildLeaveEvent) {
        GlobalScope.launch { SettingsTable.deleteSettings(event.guild) }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        GlobalScope.launch { onGuildMemberJoinAsync(event) }
    }

    private suspend fun onGuildMemberJoinAsync(event: GuildMemberJoinEvent) {
        val shard = event.jda
        val guild = event.guild
        val controller = guild.controller
        val member = event.member
        val user = member.user
        val guildSettings = getGuildSettings(guild, bot.config)

        if (guildSettings.inviteLinkRemover) {
            if (isInviteLinkBlacklisted(user.name)) {
                tryhardAsync { controller.kick(member).await() }
                return
            }
        }

        if (guildSettings.welcomeMessage) {
            val textChannelId = guildSettings.welcomeMessageChannelId
            val channel = shard.getTextChannelById(textChannelId)
            if (channel != null) {
                var message = guildSettings.message
                        .replace("\$user", member.asMention)
                        .replace("\$guild", guild.name)
                if (guildSettings.holdingRoom) {
                    val waitTime = guildSettings.holdingRoomMinutes.toString()
                    message = message.replace("\$minute", waitTime)
                }

                channel.trySendMessage(message)
            }
        }

        if (guildSettings.holdingRoom) {
            val waitTime = guildSettings.holdingRoomMinutes
            val currentTime = System.currentTimeMillis() / 1000

            JoinsTable.insertJoin(
                    JoinEntity(
                            userId = member.user.idLong,
                            guildId = guild.idLong,
                            joinTime = currentTime,
                            allowTime = currentTime + waitTime * 60,
                            allowed = false
                    )
            )
        }

        if (guildSettings.joinCaptcha) {
            val captchaUrl = "${bot.config[ServerConfig.self_url]}captcha/${guild.id}/${user.id}"
            user.trySendMessage("Welcome to ${guild.name}! To enter you must complete this captcha.\n$captchaUrl")
        }

        val records = MutesTable.fetchValidUserMutes(guild, user)

        if (records.isEmpty()) {
            return
        }

        val mutedRole: Role = tryhardAsync { Mute.setupMutedRole(guild) } ?: return
        tryhardAsync { controller.addSingleRoleToMember(member, mutedRole).await() }
    }

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent) {
        GlobalScope.launch {
            JoinsTable.deleteUserJoins(event.guild, event.user)
        }
    }

    private suspend fun executeCommand(event: GuildMessageReceivedEvent, settings: SettingsEntity, command: Command, commandName: String, args: String) {
        val shard = event.jda
        val message = event.message
        val channel = event.channel

        var showUsage = false
        try {
            showUsage = command.run(bot, event, settings, args)
        } catch (e: Exception) {
            message.failReact(bot)
            channel.trySendMessage("There was an error running your command, this incident has been logged.")
            log.error("$commandName failed with arguments $args in guild ${event.guild.name} - ${event.guild.id}", e)
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
                    message.deleteCommandMessage(settings)
                    return
                }
            }
        }
    }
}
