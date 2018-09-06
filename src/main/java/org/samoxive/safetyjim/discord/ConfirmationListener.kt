package org.samoxive.safetyjim.discord

import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class ConfirmationListener(private val threadPool: ExecutorService) : ListenerAdapter() {
    private val confirmations: MutableMap<Pair<TextChannel, User>, CompletableFuture<Message?>> = mutableMapOf()

    fun submitConfirmation(channel: TextChannel, user: User): Future<Message?> {
        val future = CompletableFuture<Message?>()
        val pair = channel to user
        confirmations[pair]?.complete(null)
        confirmations[pair] = future
        threadPool.execute {
            Thread.sleep(30 * 1000)
            val confirmation = confirmations[pair]
            if (confirmation === future) {
                confirmation.complete(null)
            }
            confirmations.remove(pair)
        }
        return future
    }

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        val author = event.author
        val channel = event.channel
        val message = event.message
        val confirmation = confirmations[channel to author] ?: return

        val completed = when (message.contentRaw.toLowerCase()) {
            "y", "ye", "yep", "yes", "yeah", "yea" -> {
                confirmation.complete(message)
                true
            }
            "n", "nah", "no", "nope" -> {
                confirmation.complete(null)
                true
            }
            else -> false
        }

        if (completed) {
            confirmations.remove(channel to author)
        }
    }
}