package org.samoxive.safetyjim.discord.processors

import net.dv8tion.jda.core.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimMessage
import org.samoxive.safetyjim.database.getGuildSettings
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordShard
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.MessageProcessor

class MessageStats : MessageProcessor() {
    override fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent): Boolean {
        shard.threadPool.submit {
            val guild = event.guild
            val guildSettings = getGuildSettings(guild, bot.config)
            if (!guildSettings.statistics) {
                return@submit
            }

            val message = event.message
            val channel = event.channel
            val content = message.contentRaw
            val user = event.member.user
            val wordCount = content.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().size
            transaction {
                JimMessage.new(message.id) {
                    userid = user.id
                    channelid = channel.id
                    guildid = guild.id
                    date = DiscordUtils.getCreationTime(message.id)
                    wordcount = wordCount
                    size = content.length
                }
            }
        }

        return false
    }

    override fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {
        transaction {
            JimMessage.findById(event.messageId)?.delete()
        }
    }
}
