package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.ocpsoft.prettytime.PrettyTime
import org.samoxive.jooq.generated.Tables
import org.samoxive.safetyjim.config.JimConfig
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils

import java.awt.*
import java.util.Date

class Info : Command() {
    override val usages = arrayOf("info - displays some information about the bot")
    private val supportServer = "https://discord.io/safetyjim"
    private val githubLink = "https://github.com/samoxive/safetyjim"
    private val botInviteLink = "https://discordapp.com/oauth2/authorize?client_id=313749262687141888&permissions=268446790&scope=bot"
    private val prettyTime = PrettyTime()

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val database = bot.database
        val config = bot.config
        val currentShard = event.jda
        val shards = bot.shards.map { shard -> shard.shard }
        val guild = event.guild
        val selfUser = currentShard.selfUser
        val message = event.message
        val channel = event.channel

        val shardCount = shards.size
        val shardId = DiscordUtils.getShardIdFromGuildId(guild.idLong, shardCount)
        val shardString = DiscordUtils.getShardString(shardId, shardCount)

        val uptimeString = prettyTime.format(bot.startTime)

        val guildCount = bot.guildCount
        val channelCount = shards
                .map { shard -> shard.textChannels.size }
                .sum()
        val userCount = shards
                .map { shard -> shard.users.size }
                .sum()
        val pingShard = currentShard.ping
        val pingAverage = shards
                .map { shard -> shard.ping }
                .sum() / shardCount

        val runtime = Runtime.getRuntime()
        val ramTotal = runtime.totalMemory() / (1024 * 1024)
        val ramUsed = ramTotal - runtime.freeMemory() / (1024 * 1024)

        val lastBanRecord = database.selectFrom(Tables.BANLIST)
                .where(Tables.BANLIST.GUILDID.eq(guild.id))
                .orderBy(Tables.BANLIST.BANTIME.desc())
                .fetchAny()

        var daysSince = "\u221E" // Infinity symbol

        if (lastBanRecord != null) {
            val now = Date()
            val dayCount = (now.time / 1000 - lastBanRecord.bantime!!) / (60 * 60 * 24)
            daysSince = java.lang.Long.toString(dayCount)
        }

        val embed = EmbedBuilder()
        embed.setAuthor(String.format("Safety Jim - v%s - Shard %s", config[JimConfig.version], shardString), null, selfUser.avatarUrl)
        embed.setDescription("Lifting the :hammer: since $uptimeString")
        embed.addField("Server Count", java.lang.Long.toString(guildCount), true)
        embed.addField("User Count", Integer.toString(userCount), true)
        embed.addField("Channel Count", Integer.toString(channelCount), true)
        embed.addField("Websocket Ping", String.format("Shard %s: %dms\nAverage: %dms", shardString, pingShard, pingAverage), true)
        embed.addField("RAM usage", String.format("%dMB / %dMB", ramUsed, ramTotal), true)
        embed.addField("Links", String.format("[Support](%s) | [Github](%s) | [Invite](%s)", supportServer, githubLink, botInviteLink), true)
        embed.setFooter("Made by Safety Jim team. | Days since last incident: $daysSince", null)
        embed.setColor(Color(0x4286F4))

        DiscordUtils.successReact(bot, message)
        DiscordUtils.sendMessage(channel, embed.build())

        return false
    }
}
