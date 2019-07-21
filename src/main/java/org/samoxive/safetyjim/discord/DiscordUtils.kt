package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.requests.Request
import net.dv8tion.jda.core.requests.Response
import net.dv8tion.jda.core.requests.RestAction
import net.dv8tion.jda.core.requests.Route
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.tryhardAsync
import java.awt.Color
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val SUCCESS_EMOTE_ID = "322698554294534144"
private const val SUCCESS_EMOTE_NAME = "jimsuccess"
private const val FAIL_EMOTE_ID = "322698553980092417"
private const val FAIL_EMOTE_NAME = "jimfail"

enum class ModLogAction(val embedColor: Color, val expirationString: String? = null) {
    Ban(Color(0xFF2900), "Banned until"),
    Kick(Color(0xFF9900)),
    Warn(Color(0xFFEB00)),
    Mute(Color(0xFFFFFF), "Muted until"),
    Softban(Color(0xFF55DD)),
    Hardban(Color(0x700000))
}

suspend fun Message.askConfirmation(bot: DiscordBot, targetUser: User): Message? {
    val jimMessage = channel.trySendMessage("You selected user ${targetUser.getUserTagAndId()}. Confirm? (type yes/no)")
    val discordShard = bot.shards[getShardIdFromGuildId(guild.idLong, bot.config[JimConfig.shard_count])]
    val confirmationMessage = discordShard.confirmationListener.submitConfirmation(textChannel, author)
    if (confirmationMessage == null) {
        failReact()
    }

    tryhardAsync { jimMessage?.delete()?.await() }
    return confirmationMessage
}

suspend fun createModLogEntry(guild: Guild, channel: TextChannel? = null, settings: SettingsEntity, modUser: User, targetUser: User, reason: String, action: ModLogAction, entityId: Int, expirationDate: Date? = null) {
    val now = Date()

    val modLogActive = settings.modLog
    val prefix = settings.prefix

    if (!modLogActive) {
        return
    }

    val modLogChannel = guild.getTextChannelById(settings.modLogChannelId)
    if (modLogChannel == null) {
        channel?.trySendMessage("Invalid moderator log channel in guild configuration, set a proper one via `$prefix settings` command.")
        return
    }

    val embed = EmbedBuilder()
    embed.setColor(action.embedColor)
    embed.addField("Action ", "${action.name} - #$entityId", false)
    embed.addField("User:", targetUser.getUserTagAndId(), false)
    embed.addField("Reason:", truncateForEmbed(reason), false)
    embed.addField("Responsible Moderator:", modUser.getUserTagAndId(), false)
    embed.setTimestamp(now.toInstant())

    if (channel != null) {
        embed.addField("Channel", channel.getChannelMention(), false)
    }

    if (action.expirationString != null) {
        val dateText = expirationDate?.toString() ?: "Indefinitely"
        embed.addField(action.expirationString, dateText, false)
    }

    modLogChannel.trySendMessage(embed.build())
}

val modDeleteCommands = arrayOf("ban", "kick", "mute", "softban", "warn", "hardban")
suspend fun Message.deleteCommandMessage(settings: SettingsEntity, commandName: String) {
    if (!settings.silentCommands) {
        return
    }

    if (settings.silentCommandsLevel == SettingsEntity.SILENT_COMMANDS_MOD_ONLY) {
        if (!modDeleteCommands.contains(commandName)) {
            return
        }
    }

    tryhardAsync { delete().await() }
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

suspend fun Message.successReact() {
    react(SUCCESS_EMOTE_NAME, SUCCESS_EMOTE_ID)
}

suspend fun Message.failMessage(errorMessage: String) {
    failReact()
    textChannel.trySendMessage(errorMessage)
}

suspend fun Message.failReact() {
    react(FAIL_EMOTE_NAME, FAIL_EMOTE_ID)
}

suspend fun Message.meloReact() {
    tryhardAsync { addReaction("\uD83C\uDF48").await() }
}

private suspend fun Message.react(emoteName: String, emoteId: String) {
    val route = Route.Messages.ADD_REACTION.compile(textChannel.id, id, "$emoteName:$emoteId")
    (object : RestAction<Void>(jda, route) {
        override fun handleResponse(response: Response, request: Request<Void>) {
            request.onSuccess(null)
        }
    }).await()
}

suspend fun MessageChannel.trySendMessage(message: String): Message? {
    return tryhardAsync { sendMessage(message).await() }
}

suspend fun MessageChannel.trySendMessage(embed: MessageEmbed): Message? {
    return tryhardAsync { sendMessage(embed).await() }
}

suspend fun MessageChannel.sendModActionConfirmationMessage(settings: SettingsEntity, message: String) {
    if (settings.modActionConfirmationMessage) {
        trySendMessage(message)
    }
}

suspend fun User.trySendMessage(embed: MessageEmbed): Message? {
    val channel = tryhardAsync { openPrivateChannel().await() }
    return channel?.trySendMessage(embed)
}

suspend fun User.trySendMessage(message: String): Message? {
    val channel = tryhardAsync { openPrivateChannel().await() }
    return channel?.trySendMessage(message)
}

suspend fun TextChannel.fetchHistoryFromScratch(): List<Message> {
    val lastMessageList = tryhardAsync { history.retrievePast(1).await() } ?: return listOf()
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
        val fetchedMessages = tryhardAsync { getHistoryBefore(lastFetchedMessage, 100).await() }
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
        val fetchedMessages = tryhardAsync { getHistoryAfter(lastFetchedMessage, 100).await() }
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

fun JDA.ShardInfo.getHumanString(): String = "[${shardId + 1} / $shardTotal]"

fun getExpirationTextInChannel(date: Date?): String = if (date != null) {
    "(Expires on $date)"
} else {
    "(Indefinitely)"
}

private val staffPermissions = arrayOf(Permission.ADMINISTRATOR, Permission.BAN_MEMBERS, Permission.KICK_MEMBERS, Permission.MANAGE_ROLES, Permission.MESSAGE_MANAGE)
fun Member.isStaff(): Boolean = staffPermissions.any { hasPermission(it) }

suspend fun <T> RestAction<T>.await(): T = suspendCoroutine { cont ->
    queue({ successValue -> cont.resume(successValue) }, { throwable -> cont.resumeWithException(throwable) })
}
