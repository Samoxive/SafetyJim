package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.JimSettings
import org.samoxive.safetyjim.discord.*
import java.awt.Color

class Ping : Command() {
    override val usages = arrayOf("ping - pong")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: JimSettings, args: String): Boolean {
        val shard = event.jda
        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim " + shard.shardInfo.getHumanReadableShardString(), null, shard.selfUser.avatarUrl)
        embed.setDescription(":ping_pong: Ping: ${shard.ping}ms")
        embed.setColor(Color(0x4286F4))
        event.message.successReact(bot)
        event.channel.trySendMessage(embed.build())
        return false
    }
}
