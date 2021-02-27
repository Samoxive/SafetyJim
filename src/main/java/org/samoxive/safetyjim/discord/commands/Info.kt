package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.BansTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.awt.Color
import java.util.*

class Info : Command() {
    override val usages = arrayOf("info - displays some information about the bot")
    private val supportServer = "https://discord.io/safetyjim"
    private val githubLink = "https://github.com/samoxive/safetyjim"
    private val botInviteLink = "https://discordapp.com/oauth2/authorize?client_id=313749262687141888&permissions=268446790&scope=bot"

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val currentShard = event.jda
        val shards = bot.shards.map { shard -> shard.jda }
        val guild = event.guild
        val selfUser = currentShard.selfUser
        val message = event.message
        val channel = event.channel

        val shardCount = shards.size
        val shardId = getShardIdFromGuildId(guild.idLong, shardCount)
        val shardString = getShardString(shardId, shardCount)

        val guildCount = bot.guildCount
        val userCount = bot.shards.flatMap { it.jda.guilds }.sumOf { it.memberCount }

        val pingShard = currentShard.gatewayPing
        val pingAverage = shards
            .asSequence()
            .map { shard -> shard.gatewayPing }
            .sum() / shardCount

        val runtime = Runtime.getRuntime()
        val ramTotal = runtime.totalMemory() / (1024 * 1024)
        val ramUsed = ramTotal - runtime.freeMemory() / (1024 * 1024)

        val lastBanRecord = BansTable.fetchGuildLastBan(guild)

        var daysSince = "\u221E" // Infinity symbol

        if (lastBanRecord != null) {
            val now = Date()
            val dayCount = (now.time / 1000 - lastBanRecord.banTime) / (60 * 60 * 24)
            daysSince = dayCount.toString()
        }

        val embed = EmbedBuilder()
        embed.setAuthor("Safety Jim - Shard $shardString", null, selfUser.avatarUrl)
        embed.setDescription("Lifting the :hammer: since ${bot.startTime.time}")
        embed.addField("Server Count", guildCount.toString(), true)
        embed.addField("User Count", userCount.toString(), true)
        embed.addBlankField(true)
        embed.addField("Websocket Ping", "Shard $shardString: ${pingShard}ms\nAverage: ${pingAverage}ms", true)
        embed.addField("RAM usage", "${ramUsed}MB / ${ramTotal}MB", true)
        embed.addBlankField(true)
        embed.addField("Links", "[Support]($supportServer) | [Github]($githubLink) | [Invite]($botInviteLink)", true)
        embed.setFooter("Made by Samoxive#8634. | Days since last incident: $daysSince", null)
        embed.setColor(Color(0x4286F4))

        message.successReact()
        channel.trySendMessage(embed.build(), message)

        return false
    }
}
