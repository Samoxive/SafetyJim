package org.samoxive.safetyjim.discord

import com.mashape.unirest.http.Unirest
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.requests.RestAction
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.JimSettings
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.tryAwaitAsJSON
import java.awt.Color
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val SUCCESS_EMOTE_ID = "322698554294534144"
private const val SUCCESS_EMOTE_NAME = "jimsuccess"
private const val FAIL_EMOTE_ID = "322698553980092417"
private const val FAIL_EMOTE_NAME = "jimfail"
private const val API_REACTION_URL = "https://discordapp.com/api/channels/%s/messages/%s/reactions/%s:%s/@me"

private const val DISCORD_EPOCH = 1420070400000L
private const val TIMESTAMP_OFFSET = 22

private val modLogColors = mapOf(
        "ban" to Color(0xFF2900),
        "kick" to Color(0xFF9900),
        "warn" to Color(0xFFEB00),
        "mute" to Color(0xFFFFFF),
        "softban" to Color(0xFF55DD),
        "hardban" to Color(0x700000)
)

suspend fun Message.askConfirmation(bot: DiscordBot, targetUser: User): Message? {
    val jimMessage = channel.trySendMessage("You selected user ${targetUser.getUserTagAndId()}. Confirm? (type yes/no)")
    val discordShard = bot.shards[getShardIdFromGuildId(guild.idLong, bot.config[JimConfig.shard_count])]
    val confirmationMessage = discordShard.confirmationListener.submitConfirmation(textChannel, author)
    if (confirmationMessage == null) {
        failReact(bot)
    }

    jimMessage?.delete()?.tryAwait()
    return confirmationMessage
}

suspend fun Message.createModLogEntry(shard: JDA, settings: JimSettings, user: User, reason: String, action: String, id: Int, expirationDate: Date?, expires: Boolean) {
    val now = Date()

    val modLogActive = settings.modlog
    val prefix = settings.prefix

    if (!modLogActive) {
        return
    }

    val modLogChannel = shard.getTextChannelById(settings.modlogchannelid)

    if (modLogChannel == null) {
        channel.trySendMessage("Invalid moderator log channel in guild configuration, set a proper one via `$prefix settings` command.")
        return
    }

    val embed = EmbedBuilder()
    embed.setColor(modLogColors[action])
    embed.addField("Action ", "${action.capitalize()} - #$id", false)
    embed.addField("User:", user.getUserTagAndId(), false)
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.addField("Responsible Moderator:", author.getUserTagAndId(), false)
    embed.addField("Channel", channel.getChannelMention(), false)
    embed.setTimestamp(now.toInstant())

    if (expires) {
        val dateText = expirationDate?.toString() ?: "Indefinitely"
        val untilText = when (action) {
            "ban" -> "Banned until"
            "mute" -> "Muted until"
            else -> null
        }

        embed.addField(untilText, dateText, false)
    }

    modLogChannel.trySendMessage(embed.build())
}

suspend fun Message.deleteCommandMessage(bot: DiscordBot) {
    val silentCommandsActive = getGuildSettings(guild, bot.config).silentcommands

    if (!silentCommandsActive) {
        return
    }

    delete().tryAwait()
}

fun Member.isKickableBy(kicker: Member) = isBannableBy(kicker)

fun Member.isBannableBy(banner: Member): Boolean {
    // Users can't ban themselves
    if (this == banner) {
        return false
    }

    // Owners cannot be banned
    val owner = guild.owner
    if (this == owner) {
        return false
    }

    val highestRoleSelf = getHighestRole()
    val highestRoleBanner = banner.getHighestRole()

    // If either of these variables are null, this means they have no roles
    // If the person we are trying to ban has no roles, there are two possibilities
    // Either banner also doesn't have a role, in which case both users are equal
    // and banner doesn't have the power to ban, or banner has a role which will
    // always equal to being above the to be banned user
    return if (highestRoleSelf == null || highestRoleBanner == null) {
        highestRoleBanner != null
    } else {
        highestRoleSelf.position < highestRoleBanner.position
    }
}

fun Member.isOnline(): Boolean = onlineStatus == OnlineStatus.ONLINE ||
        onlineStatus == OnlineStatus.DO_NOT_DISTURB ||
        onlineStatus == OnlineStatus.IDLE

fun Guild.isTalkable(): Boolean = textChannels.any { channel -> channel.canTalk() }

suspend fun Message.successReact(bot: DiscordBot) {
    react(bot, SUCCESS_EMOTE_NAME, SUCCESS_EMOTE_ID)
}

suspend fun Message.failMessage(bot: DiscordBot, errorMessage: String) {
    failReact(bot)
    textChannel.trySendMessage(errorMessage)
}

suspend fun Message.failReact(bot: DiscordBot) {
    react(bot, FAIL_EMOTE_NAME, FAIL_EMOTE_ID)
}

suspend fun Message.meloReact() {
    addReaction("\uD83C\uDF48").tryAwait()
}

private suspend fun Message.react(bot: DiscordBot, emoteName: String, emoteId: String) {
    val token = bot.config[JimConfig.token]
    val requestUrl = String.format(API_REACTION_URL, textChannel.id, id, emoteName, emoteId)

    Unirest.put(requestUrl)
            .header("User-Agent", "Safety Jim")
            .header("Authorization", "Bot $token")
            .tryAwaitAsJSON()
}

suspend fun MessageChannel.trySendMessage(message: String): Message? {
    return sendMessage(message).tryAwait()
}

suspend fun MessageChannel.trySendMessage(embed: MessageEmbed): Message? {
    return sendMessage(embed).tryAwait()
}

suspend fun User.trySendMessage(embed: MessageEmbed): Message? {
    val channel = openPrivateChannel().tryAwait()
    return channel?.trySendMessage(embed)
}

suspend fun User.trySendMessage(message: String): Message? {
    val channel = openPrivateChannel().tryAwait()
    return channel?.trySendMessage(message)
}

suspend fun TextChannel.fetchHistoryFromScratch(): List<Message> {
    val lastMessageList = history.retrievePast(1).tryAwait() ?: return listOf()
    if (lastMessageList.size != 1) {
        return listOf()
    }

    val lastMessage = lastMessageList[0]
    val fetchedMessages = fetchFullHistoryBeforeMessage(lastMessage)
    // we want last message to also be included
    fetchedMessages.add(lastMessage)
    return fetchedMessages
}

suspend fun TextChannel.fetchFullHistoryBeforeMessage(beforeMessage: Message): MutableList<Message> {
    val messages = ArrayList<Message>()

    var lastFetchedMessage = beforeMessage
    var lastMessageReceived = false
    while (!lastMessageReceived) {
        val fetchedMessages = getHistoryBefore(lastFetchedMessage, 100)
                .tryAwait()
                ?.retrievedHistory ?: return mutableListOf()

        messages.addAll(fetchedMessages)

        if (fetchedMessages.size < 100) {
            lastMessageReceived = true
        } else {
            lastFetchedMessage = fetchedMessages[99]
        }
    }

    return messages
}

suspend fun TextChannel.fetchFullHistoryAfterMessage(afterMessage: Message): List<Message> {
    val messages = ArrayList<Message>()

    var lastFetchedMessage = afterMessage
    var lastMessageReceived = false
    while (!lastMessageReceived) {
        val fetchedMessages = getHistoryAfter(lastFetchedMessage, 100)
                .tryAwait()
                ?.retrievedHistory ?: return listOf()

        messages.addAll(fetchedMessages)

        if (fetchedMessages.size < 100) {
            lastMessageReceived = true
        } else {
            lastFetchedMessage = fetchedMessages[99]
        }
    }

    return messages
}

fun String.getCreationTime(): Long = toLong().ushr(TIMESTAMP_OFFSET) + DISCORD_EPOCH

private fun MessageChannel.getChannelMention(): String = "<#$id>"

fun User.getUserTagAndId(): String = "${getTag()} ($id)"

private fun Member.getHighestRole() = if (roles.size == 0) {
    null
} else roles.reduce { prev, next ->
    if (prev != null) {
        if (next.position > prev.position) next else prev
    } else {
        next
    }
}

fun getUsageString(prefix: String, usages: Array<String>): String =
        usages.map { usage -> usage.split(" - ") }
                .joinToString("\n") { "`$prefix ${it[0]}` - ${it[1]}" }

fun User.getTag(): String = "$name#$discriminator"

fun Guild.getDefaultChannelTalkable(): TextChannel {
    for (channel in textChannels) {
        if (channel.canTalk()) {
            return channel
        }
    }

    return textChannels[0]
}

// (guild_id >> 22) % num_shards == shard_id
fun getShardIdFromGuildId(guildId: Long, shardCount: Int): Int = ((guildId shr 22) % shardCount).toInt()

fun getShardString(shardId: Int, shardCount: Int): String = "[${shardId + 1} / $shardCount]"

fun JDA.ShardInfo.getHumanReadableShardString(): String = "[${shardId + 1} / $shardTotal]"

fun getExpirationTextInChannel(date: Date?): String = if (date != null) {
    "(Expires on $date)"
} else {
    "(Indefinitely)"
}

suspend fun <T> RestAction<T>.await(): T = suspendCoroutine { cont ->
    queue({ successValue -> cont.resume(successValue) }, { throwable -> cont.resumeWithException(throwable) })
}

suspend fun <T> RestAction<T>.tryAwait(): T? = suspendCoroutine { cont ->
    queue({ successValue -> cont.resume(successValue) }, { cont.resume(null) })
}