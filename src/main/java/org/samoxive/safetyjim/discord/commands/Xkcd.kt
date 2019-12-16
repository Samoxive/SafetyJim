package org.samoxive.safetyjim.discord.commands

import io.vertx.kotlin.ext.web.client.sendAwait
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.jsoup.Jsoup
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.httpClient

private val xkcdURLPattern = "^https?://(www\\.)?xkcd\\.com/\\d+/\$".toRegex()
private const val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.169 Safari/537.36"

class Xkcd : Command() {
    override val usages = arrayOf<String>()

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val message = event.message
        val channel = event.channel
        val searchQuery = if (args.isNotEmpty()) {
            "$args xkcd"
        } else {
            return true
        }

        val response = httpClient.get(443, "duckduckgo.com", "/html/")
            .putHeader("User-Agent", userAgent)
            .addQueryParam("q", searchQuery)
            .sendAwait()

        val htmlBody = response.bodyAsString()
        if (htmlBody == null) {
            message.failMessage("Failed to search given query!")
            return false
        }

        val document = Jsoup.parse(htmlBody)
        for (link in document.getElementsByClass("result__url")) {
            val href = link.attr("href") ?: continue
            if (xkcdURLPattern.matches(href)) {
                message.successReact()
                channel.trySendMessage(href)
                return false
            }
        }

        message.failMessage("Failed to find a relevant xkcd comic!")
        return false
    }
}
