package org.samoxive.safetyjim.discord.processors

import io.vertx.kotlin.ext.web.client.sendAwait
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.ahocorasick.trie.Trie
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.database.SettingsTable
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.discord.commands.*
import org.samoxive.safetyjim.httpClient
import org.samoxive.safetyjim.tryhardAsync
import java.time.Instant
import java.util.*

private const val DEFAULT_BLACKLIST_URL = "https://raw.githubusercontent.com/Samoxive/Google-profanity-words/master/list.txt"
private const val ACTION_REASON = "Using blacklisted word(s)."

class WordFilter : MessageProcessor() {
    private lateinit var defaultWordFilterLow: Trie
    private lateinit var defaultWordFilterHigh: Trie

    init {
        runBlocking {
            val blacklist = httpClient.getAbs(DEFAULT_BLACKLIST_URL)
                    .sendAwait()
                    .bodyAsString()
            val blacklistWords = blacklist.split("\n")
                    .map { it.toLowerCase().trim() }
                    .filter { it.isNotEmpty() }
            defaultWordFilterLow = Trie.builder()
                    .addKeywords(
                            blacklistWords
                    )
                    .ignoreCase()
                    .onlyWholeWords()
                    .stopOnHit()
                    .build()
            defaultWordFilterHigh = Trie.builder()
                    .addKeywords(
                            blacklistWords
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

        val filter = if (settings.wordFilterBlacklist != null) {
            SettingsTable.getWordFilter(settings)
        } else {
            when (settings.wordFilterLevel) {
                SettingsEntity.WORD_FILTER_LEVEL_LOW -> defaultWordFilterLow
                SettingsEntity.WORD_FILTER_LEVEL_HIGH -> defaultWordFilterHigh
                else -> throw IllegalStateException()
            }
        }

        val blacklistHits = filter.parseText(event.message.contentRaw)
        if (blacklistHits.isEmpty()) {
            return false
        }

        val expirationDate = if (settings.wordFilterAction == SettingsEntity.ACTION_BAN || settings.wordFilterAction == SettingsEntity.ACTION_MUTE) {
            if (settings.wordFilterActionDuration == 0) {
                null
            } else {
                Date.from(Instant.now().plusSeconds(settings.getWordFilterActionDurationDelta().toLong()))
            }
        } else {
            null
        }

        tryhardAsync {
            message.delete().await()
            when (settings.wordFilterAction) {
                SettingsEntity.ACTION_NOTHING -> {}
                SettingsEntity.ACTION_WARN -> warnAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                SettingsEntity.ACTION_MUTE -> muteAction(guild, channel, settings, selfUser, targetUser, null, ACTION_REASON, expirationDate)
                SettingsEntity.ACTION_KICK -> kickAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                SettingsEntity.ACTION_BAN -> banAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON, expirationDate)
                SettingsEntity.ACTION_SOFTBAN -> softbanAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                SettingsEntity.ACTION_HARDBAN -> hardbanAction(guild, channel, settings, selfUser, targetUser, ACTION_REASON)
                else -> throw IllegalStateException()
            }
        }

        return true
    }

    override suspend fun onMessageDelete(bot: DiscordBot, shard: DiscordShard, event: GuildMessageDeleteEvent) {}
}