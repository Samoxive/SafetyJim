package org.samoxive.safetyjim.discord

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.ExceptionEvent
import net.dv8tion.jda.api.events.ReadyEvent
import net.dv8tion.jda.api.events.guild.GuildJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.SessionController
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.samoxive.safetyjim.database.*
import org.samoxive.safetyjim.database.SettingsTable.getGuildSettings
import org.samoxive.safetyjim.discord.commands.setupMutedRole
import org.samoxive.safetyjim.discord.processors.isInviteLinkBlocklisted
import org.samoxive.safetyjim.tryhardAsync
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import javax.security.auth.login.LoginException
import kotlin.system.exitProcess

const val DEPRECATION_NOTICE = "Due to Discord's changes, the old way of implementing commands as text messages has been deprecated.\n" +
    "Safety Jim will migrate to slash commands on 8th of January after much work rewriting most of the project to adapt to changes.\n" +
    "For this migration there is no action needed, however if you invited Jim after 24th of March, 2021 you need to kick him and invite back for slash commands to appear in your server.\n" +
    "You can use the whois command with Jim to find when you invited him.\n\n" +
    "To check out the new slash commands before migration and report potential bugs and feedback, feel free to head to the support Discord server."

class DiscordShard(private val bot: DiscordBot, shardId: Int, sessionController: SessionController) : ListenerAdapter() {
    private val log: Logger
    val jda: JDA
    val confirmationListener = ConfirmationListener()

    init {
        val config = bot.config
        val shardString = getShardString(shardId, config.jim.shard_count)
        log = LoggerFactory.getLogger("DiscordShard $shardString")

        this.jda = try {
            JDABuilder.create(
                config.jim.token,
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES
            )
                .setChunkingFilter(ChunkingFilter.NONE)
                .addEventListeners(this, confirmationListener)
                .setSessionController(sessionController) // needed to prevent shards trying to reconnect too soon
                .useSharding(shardId, config.jim.shard_count)
                .disableCache(
                    EnumSet.of(
                        CacheFlag.ACTIVITY,
                        CacheFlag.VOICE_STATE,
                        CacheFlag.EMOTE,
                        CacheFlag.CLIENT_STATUS,
                        CacheFlag.ONLINE_STATUS
                    )
                )
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setActivity(Activity.playing("safetyjim.xyz $shardString"))
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
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        GlobalScope.launch { onGuildMessageReceivedAsync(event) }
    }

    private suspend fun onGuildMessageReceivedAsync(event: GuildMessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }

        // make sure event#member is not null, it's used quite a lot
        if (event.isWebhookMessage) {
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
            message.successReact()

            val embed = EmbedBuilder()
            embed.setAuthor("Safety Jim - Prefix", null, self.avatarUrl)
                .setDescription("This guild's prefix is: $prefix")
                .setColor(Color(0x4286F4))

            message.textChannel.trySendMessage(embed.build(), message)
            return
        }

        val guildSettings = getGuildSettings(guild, bot.config)

        // Spread processing jobs across threads as they are likely to be independent of io operations
        val processorResult = bot.processors.map {
            GlobalScope.async {
                tryhardAsync {
                    it.onMessage(bot, this@DiscordShard, event, guildSettings)
                } ?: false
            }
        }.map { it.await() }.reduce { acc, elem -> acc || elem }

        // If processors return true, that means they deleted the original message, so we don't need to continue further
        if (processorResult) {
            return
        }

        val prefix = guildSettings.prefix.lowercase()

        // 0 = prefix, 1 = command, rest are accepted as arguments
        val splitContent = content.trim().split(" ").toTypedArray()
        val firstWord = splitContent[0].lowercase()
        val command: Command?
        val commandName: String

        if (!guildSettings.noSpacePrefix) {
            if (firstWord != prefix) {
                return
            }

            // This means the user only entered the prefix
            if (splitContent.size == 1) {
                message.failReact()
                return
            }

            // We also want commands to be case-insensitive
            commandName = splitContent[1].lowercase()
            command = bot.commands[commandName]
        } else {
            if (!firstWord.startsWith(prefix)) {
                return
            }

            if (firstWord.length == prefix.length) {
                message.failReact()
                return
            }

            commandName = firstWord.substring(prefix.length)
            command = bot.commands[commandName]
        }

        // Command not found
        if (command == null) {
            message.failReact()
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

            val defaultPrefix = bot.config.jim.default_prefix
            val message = "Hello! I am Safety Jim, `$defaultPrefix` is my default prefix! Visit https://safetyjim.xyz/commands to see available commands.\nYou can join the support server at https://discord.io/safetyjim or contact Samoxive#8634 for help."
            guild.getDefaultChannelTalkable()?.trySendMessage(message)
            SettingsTable.insertDefaultGuildSettings(bot.config, guild)
        }
    }

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        GlobalScope.launch { onGuildMemberJoinAsync(event) }
    }

    private suspend fun onGuildMemberJoinAsync(event: GuildMemberJoinEvent) {
        val shard = event.jda
        val guild = event.guild
        val member = event.member
        val user = member.user
        val guildSettings = getGuildSettings(guild, bot.config)

        if (guildSettings.inviteLinkRemover) {
            if (isInviteLinkBlocklisted(user.name)) {
                tryhardAsync { guild.kick(member).await() }
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
            val captchaUrl = "${bot.config.server.self_url}captcha/${guild.id}/${user.id}"
            user.trySendMessage("Welcome to ${guild.name}! To enter you must complete this captcha.\n$captchaUrl")
        }

        val records = MutesTable.fetchValidUserMutes(guild, user)

        if (records.isEmpty()) {
            return
        }

        val mutedRole: Role = tryhardAsync { setupMutedRole(guild) } ?: return
        tryhardAsync { guild.addRoleToMember(member, mutedRole).await() }
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
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
            message.failReact()
            channel.trySendMessage("There was an error running your command, this incident has been logged.", message)
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

            message.failReact()
            channel.trySendMessage(embed.build(), message)
        } else {
            message.deleteCommandMessage(settings, commandName)
        }

        val guildId = event.guild.idLong
        if (!bot.notifiedGuilds.contains(guildId)) {
            bot.notifiedGuilds.add(guildId)
            channel.trySendMessage(DEPRECATION_NOTICE)
        }
    }
}
