package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.getHumanReadableShardString
import org.samoxive.safetyjim.discord.successReact
import java.awt.Color

class Ping : Command() {
    override val usages = arrayOf("ping - pong")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val shard = event.jda
        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim " + shard.shardInfo.getHumanReadableShardString(), null, shard.selfUser.avatarUrl)
        embed.setDescription(":ping_pong: Ping: ${shard.ping}ms")
        embed.setColor(Color(0x4286F4))
        event.message.successReact(bot)
        event.channel.sendMessage(embed.build())
        return false
    }
}
