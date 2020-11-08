package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.awt.Color

class Ping : Command() {
    override val usages = arrayOf("ping - pong")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val shard = event.jda
        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim ${shard.shardInfo.getHumanString()}", null, shard.selfUser.avatarUrl)
        embed.setDescription(":ping_pong: Ping: ${shard.gatewayPing}ms")
        embed.setColor(Color(0x4286F4))
        event.message.successReact()
        event.channel.trySendMessage(embed.build())
        return false
    }
}
