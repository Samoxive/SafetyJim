package org.samoxive.safetyjim.discord

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ConfirmationListener : ListenerAdapter() {
    private val confirmations: MutableMap<Pair<TextChannel, User>, Continuation<Message?>> = mutableMapOf()

    suspend fun submitConfirmation(channel: TextChannel, user: User): Message? = suspendCoroutine { cont ->
        val pair = channel to user
        confirmations[pair]?.resume(null)
        confirmations[pair] = cont
        GlobalScope.launch {
            delay(30 * 1000)
            val confirmation = confirmations[pair]
            if (confirmation === cont) {
                confirmation.resume(null)
            }
            confirmations.remove(pair)
        }
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        val author = event.author
        val channel = event.channel
        val message = event.message
        val confirmation = confirmations[channel to author] ?: return

        val completed = when (message.contentRaw.toLowerCase()) {
            "y", "ye", "yep", "yes", "yeah", "yea" -> {
                confirmation.resume(message)
                true
            }
            "n", "nah", "no", "nope" -> {
                confirmation.resume(null)
                true
            }
            else -> false
        }

        if (completed) {
            confirmations.remove(channel to author)
        }
    }
}