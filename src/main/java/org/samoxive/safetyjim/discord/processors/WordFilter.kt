package org.samoxive.safetyjim.discord.processors

import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.ahocorasick.trie.Trie
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SettingsTable
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.httpClient
import org.samoxive.safetyjim.tryhardAsync

private const val DEFAULT_BLOCKLIST_URL = "https://raw.githubusercontent.com/Samoxive/Google-profanity-words/master/list.txt"
private const val ACTION_REASON = "Using blocklisted word(s)."

class WordFilter : MessageProcessor() {
    private var defaultWordFilterLow: Trie
    private var defaultWordFilterHigh: Trie

    init {
        runBlocking {
            val blocklist = httpClient.getAbs(DEFAULT_BLOCKLIST_URL)
                .send()
                .await()
                .bodyAsString()
            val blocklistWords = blocklist.split("\n")
                .map { it.lowercase().trim() }
                .filter { it.isNotEmpty() }
            defaultWordFilterLow = Trie.builder()
                .addKeywords(
                    blocklistWords
                )
                .ignoreCase()
                .onlyWholeWords()
                .stopOnHit()
                .build()
            defaultWordFilterHigh = Trie.builder()
                .addKeywords(
                    blocklistWords
                )
                .ignoreCase()
                .build()
        }
    }

    override suspend fun onMessage(bot: DiscordBot, shard: DiscordShard, event: GuildMessageReceivedEvent, settings: SettingsEntity): Boolean {
        val message = event.message
        val member = event.member!!
        val channel = event.channel
        val guild = event.guild
        val selfUser = event.jda.selfUser
        val targetUser = event.author

        if (!settings.wordFilter) {
            return false
        }

        if (member.isStaff()) {
            return false
        }

        val filter = if (settings.wordFilterBlocklist != null) {
            SettingsTable.getWordFilter(settings)
        } else {
            when (settings.wordFilterLevel) {
                SettingsEntity.WORD_FILTER_LEVEL_LOW -> defaultWordFilterLow
                SettingsEntity.WORD_FILTER_LEVEL_HIGH -> defaultWordFilterHigh
                else -> throw IllegalStateException()
            }
        }

        val blocklistHits = filter.parseText(event.message.contentRaw)
        if (blocklistHits.isEmpty()) {
            return false
        }

        val expirationDate = settings.getWordFilterActionExpirationDate()

        tryhardAsync {
            message.delete().await()
            executeModAction(settings.wordFilterAction, guild, channel, settings, selfUser, targetUser, ACTION_REASON, expirationDate)
        }

        return true
    }

    override suspend fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {}
}
