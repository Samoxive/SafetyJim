package org.samoxive.safetyjim.discord

import com.mashape.unirest.http.Unirest
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.OnlineStatus
import net.dv8tion.jda.core.entities.*
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.database.getGuildSettings
import java.awt.Color
import java.util.*

fun askConfirmation(bot: DiscordBot, message: Message, targetUser: User): Message? {
    val channel = message.textChannel
    val user = message.author
    val guild = message.guild
    val jimMessage = try {
        channel.sendMessage("You selected user ${DiscordUtils.getUserTagAndId(targetUser)}. Confirm?").complete()
    } catch (e: Exception) {
        null
    }
    val discordShard = bot.shards[DiscordUtils.getShardIdFromGuildId(guild.idLong, bot.config[JimConfig.shard_count])]
    val confirmationMessage = discordShard.confirmationListener.submitConfirmation(channel, user).get()
    if (confirmationMessage == null) {
        DiscordUtils.failReact(bot, message)
    }
    try {
        jimMessage?.delete()?.complete()
    } catch (e: Exception) {
        //
    }
    return confirmationMessage
}

object DiscordUtils {
    private const val SUCCESS_EMOTE_ID = "322698554294534144"
    private const val SUCCESS_EMOTE_NAME = "jimsuccess"
    private const val FAIL_EMOTE_ID = "322698553980092417"
    private const val FAIL_EMOTE_NAME = "jimfail"
    private const val API_REACTION_URL = "https://discordapp.com/api/channels/%s/messages/%s/reactions/%s:%s/@me"

    private const val DISCORD_EPOCH = 1420070400000L
    private const val TIMESTAMP_OFFSET = 22

    private val modLogColors: MutableMap<String, Color> = HashMap()

    private val modLogActionTexts: MutableMap<String, String> = HashMap()

    init {
        modLogColors["ban"] = Color(0xFF2900)
        modLogColors["kick"] = Color(0xFF9900)
        modLogColors["warn"] = Color(0xFFEB00)
        modLogColors["mute"] = Color(0xFFFFFF)
        modLogColors["softban"] = Color(0xFF55DD)
        modLogColors["hardban"] = Color(0x700000)
    }

    init {
        modLogActionTexts["ban"] = "Ban"
        modLogActionTexts["softban"] = "Softban"
        modLogActionTexts["kick"] = "Kick"
        modLogActionTexts["warn"] = "Warn"
        modLogActionTexts["mute"] = "Mute"
        modLogActionTexts["hardban"] = "Hardban"
    }

    fun createModLogEntry(bot: DiscordBot, shard: JDA, message: Message, user: User, reason: String, action: String, id: Int, expirationDate: Date?, expires: Boolean) {
        val guildSettings = getGuildSettings(message.guild, bot.config)
        val now = Date()

        val modLogActive = guildSettings.modlog
        val prefix = guildSettings.prefix

        if (!modLogActive) {
            return
        }

        val modLogChannel = shard.getTextChannelById(guildSettings.modlogchannelid)

        if (modLogChannel == null) {
            sendMessage(message.channel, "Invalid moderator log channel in guild configuration, set a proper one via `$prefix settings` command.")
            return
        }

        val embed = EmbedBuilder()
        val channel = message.textChannel
        embed.setColor(modLogColors[action])
        embed.addField("Action ", modLogActionTexts[action] + " - #" + id, false)
        embed.addField("User:", getUserTagAndId(user), false)
        embed.addField("Reason:", truncateForEmbed(reason), false)
        embed.addField("Responsible Moderator:", getUserTagAndId(message.author), false)
        embed.addField("Channel", getChannelMention(channel), false)
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

        sendMessage(modLogChannel, embed.build())
    }

    fun deleteCommandMessage(bot: DiscordBot, message: Message) {
        val silentCommandsActive = getGuildSettings(message.guild, bot.config).silentcommands

        if (!silentCommandsActive) {
            return
        }

        try {
            message.delete().complete()
        } catch (e: Exception) {
            //
        }
    }

    fun isKickable(toKick: Member, kicker: Member): Boolean {
        return isBannable(toKick, kicker)
    }

    fun isBannable(toBan: Member, banner: Member): Boolean {
        val guild = toBan.guild
        val toBanUser = toBan.user
        val bannerUser = banner.user

        // Users can't ban themselves
        if (bannerUser.id == toBanUser.id) {
            return false
        }

        // Owners cannot be banned
        val ownerId = guild.owner.user.id
        if (ownerId == toBanUser.id) {
            return false
        }

        val highestRoleToBan = getHighestRole(toBan)
        val highestRoleBanner = getHighestRole(banner)

        // If either of these variables are null, this means they have no roles
        // If the person we are trying to ban has no roles, there are two possibilities
        // Either banner also doesn't have a role, in which case both users are equal
        // and banner doesn't have the power to ban, or banner has a role which will
        // always equal to being above the to be banned user
        return if (highestRoleToBan == null || highestRoleBanner == null) {
            highestRoleBanner != null
        } else highestRoleToBan.position < highestRoleBanner.position
    }

    fun isOnline(member: Member): Boolean {
        val status = member.onlineStatus

        return status == OnlineStatus.ONLINE ||
                status == OnlineStatus.DO_NOT_DISTURB ||
                status == OnlineStatus.IDLE
    }

    fun isGuildTalkable(guild: Guild): Boolean {
        val channels = guild.textChannels
                .filter { channel -> channel.canTalk() }

        return channels.isNotEmpty()
    }

    fun successReact(bot: DiscordBot, message: Message) {
        reactToMessage(bot, message, SUCCESS_EMOTE_NAME, SUCCESS_EMOTE_ID)
    }

    fun failMessage(bot: DiscordBot, message: Message, errorMessage: String) {
        failReact(bot, message)
        sendMessage(message.textChannel, errorMessage)
    }

    fun failReact(bot: DiscordBot, message: Message) {
        reactToMessage(bot, message, FAIL_EMOTE_NAME, FAIL_EMOTE_ID)
    }

    private fun reactToMessage(bot: DiscordBot, message: Message, emoteName: String, emoteId: String) {
        val channelId = message.textChannel.id
        val messageId = message.id
        val token = bot.config[JimConfig.token]
        val requestUrl = String.format(API_REACTION_URL, channelId, messageId, emoteName, emoteId)

        try {
            Unirest.put(requestUrl)
                    .header("User-Agent", "Safety Jim")
                    .header("Authorization", "Bot $token")
                    .asJson()
        } catch (e: Exception) {
            //
        }
    }

    fun sendMessage(channel: MessageChannel, message: String) {
        try {
            channel.sendMessage(message).complete()
        } catch (e: Exception) {
            //
        }
    }

    fun sendMessage(channel: MessageChannel, embed: MessageEmbed) {
        try {
            channel.sendMessage(embed).queue()
        } catch (e: Exception) {
            //
        }
    }

    fun sendDM(user: User, message: String) {
        try {
            val channel = user.openPrivateChannel().complete()
            sendMessage(channel, message)
        } catch (e: Exception) {
            //
        }
    }

    fun sendDM(user: User, embed: MessageEmbed) {
        try {
            val channel = user.openPrivateChannel().complete()
            sendMessage(channel, embed)
        } catch (e: Exception) {
            //
        }
    }

    fun getUserById(shard: JDA, userId: String): User {
        var user: User? = shard.getUserById(userId)

        if (user == null) {
            user = shard.retrieveUserById(userId).complete()
        }

        return user!!
    }

    fun fetchHistoryFromScratch(channel: TextChannel): List<Message> {
        val lastMessageList = channel.history.retrievePast(1).complete()
        if (lastMessageList.size != 1) {
            return ArrayList()
        }

        val lastMessage = lastMessageList[0]
        val fetchedMessages = DiscordUtils.fetchFullHistoryBeforeMessage(channel, lastMessage)
        // we want last message to also be included
        fetchedMessages.add(lastMessage)
        return fetchedMessages
    }

    fun fetchFullHistoryBeforeMessage(channel: TextChannel, beforeMessage: Message): MutableList<Message> {
        val messages = ArrayList<Message>()

        var lastFetchedMessage = beforeMessage
        var lastMessageReceived = false
        while (!lastMessageReceived) {
            val fetchedMessages = channel.getHistoryBefore(lastFetchedMessage, 100)
                    .complete()
                    .retrievedHistory

            messages.addAll(fetchedMessages)

            if (fetchedMessages.size < 100) {
                lastMessageReceived = true
            } else {
                lastFetchedMessage = fetchedMessages[99]
            }
        }

        return messages
    }

    fun fetchFullHistoryAfterMessage(channel: TextChannel, afterMessage: Message): List<Message> {
        val messages = ArrayList<Message>()

        var lastFetchedMessage = afterMessage
        var lastMessageReceived = false
        while (!lastMessageReceived) {
            val fetchedMessages = channel.getHistoryAfter(lastFetchedMessage, 100)
                    .complete()
                    .retrievedHistory

            messages.addAll(fetchedMessages)

            if (fetchedMessages.size < 100) {
                lastMessageReceived = true
            } else {
                lastFetchedMessage = fetchedMessages[99]
            }
        }

        return messages
    }

    fun getCreationTime(id: String): Long {
        val idLong = id.toLong()
        return idLong.ushr(TIMESTAMP_OFFSET) + DISCORD_EPOCH
    }

    private fun getChannelMention(channel: MessageChannel): String {
        return "<#" + channel.id + ">"
    }

    fun getUserTagAndId(user: User): String {
        return getTag(user) + " (" + user.id + ")"
    }

    private fun getHighestRole(member: Member): Role? {
        val roles = member.roles

        return if (roles.size == 0) {
            null
        } else roles.reduce { prev, next ->
            if (prev != null) {
                if (next.position > prev.position) next else prev
            } else {
                next
            }
        }
    }

    fun getUsageString(prefix: String, usages: Array<String>): String {
        val joiner = StringJoiner("\n")

        usages.map { usage -> usage.split(" - ").dropLastWhile { it.isEmpty() }.toTypedArray() }
                .map { splitUsage -> String.format("`%s %s` - %s", prefix, splitUsage[0], splitUsage[1]) }
                .forEach { usage -> joiner.add(usage) }

        return joiner.toString()
    }

    fun getTag(user: User): String {
        return user.name + "#" + user.discriminator
    }

    fun getDefaultChannel(guild: Guild): TextChannel {
        val channels = guild.textChannels
        for (channel in channels) {
            if (channel.canTalk()) {
                return channel
            }
        }

        return channels[0]
    }

    fun getShardIdFromGuildId(guildId: Long, shardCount: Int): Int {
        // (guild_id >> 22) % num_shards == shard_id
        return ((guildId shr 22) % shardCount).toInt()
    }

    fun getShardString(shardId: Int, shardCount: Int): String {
        return "[" + (shardId + 1) + " / " + shardCount + "]"
    }

    fun getShardString(shardInfo: JDA.ShardInfo): String {
        val shardId = shardInfo.shardId
        val shardCount = shardInfo.shardTotal

        return "[" + (shardId + 1) + " / " + shardCount + "]"
    }

    fun getGuildFromBot(bot: DiscordBot, guildId: String): Guild? {
        val shards = bot.shards
        val guildIdLong: Long
        try {
            guildIdLong = guildId.toLong()
        } catch (e: NumberFormatException) {
            return null
        }

        val shardId = getShardIdFromGuildId(guildIdLong, shards.size)
        return shards[shardId].shard.getGuildById(guildId)
    }
}
