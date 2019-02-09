package org.samoxive.safetyjim.discord.processors

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.JimMessage
import org.samoxive.safetyjim.database.awaitTransaction
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordShard
import org.samoxive.safetyjim.discord.MessageProcessor
import org.samoxive.safetyjim.discord.getCreationTime

class MessageStats : MessageProcessor() {
    override suspend fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent): Boolean {
        GlobalScope.launch {
            val guild = event.guild
            val guildSettings = getGuildSettings(guild, bot.config)
            if (!guildSettings.statistics) {
                return@launch
            }

            val message = event.message
            val channel = event.channel
            val content = message.contentRaw
            val user = event.member.user
            val wordCount = content.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray().size
            awaitTransaction {
                JimMessage.new(message.idLong) {
                    userid = user.idLong
                    channelid = channel.idLong
                    guildid = guild.idLong
                    date = message.id.getCreationTime()
                    wordcount = wordCount
                    size = content.length
                }
            }
        }

        return false
    }

    override suspend fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {
        awaitTransaction {
            JimMessage.findById(event.messageIdLong)?.delete()
        }
    }
}
