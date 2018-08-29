package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.jooq.generated.Tables
import org.samoxive.safetyjim.database.DatabaseUtils
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordShard
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.MessageProcessor

class MessageStats : MessageProcessor() {
    override fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent): Boolean {
        shard.threadPool.submit {
            val database = bot.database

            val guild = event.guild
            val guildSettings = DatabaseUtils.getGuildSettings(bot, database, guild)
            if (!guildSettings.statistics) {
                return@submit
            }

            val message = event.message
            val channel = event.channel
            val content = message.contentRaw
            val user = event.member.user
            val wordCount = content.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size
            val record = database.newRecord(Tables.MESSAGES)

            record.messageid = message.id
            record.userid = user.id
            record.channelid = channel.id
            record.guildid = guild.id
            record.date = DiscordUtils.getCreationTime(message.id)
            record.wordcount = wordCount
            record.size = content.length
            record.store()
        }

        return false
    }

    override fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {
        val database = bot.database
        val messageId = event.messageId
        database.deleteFrom(Tables.MESSAGES)
                .where(Tables.MESSAGES.MESSAGEID.eq(messageId))
                .execute()
    }
}
