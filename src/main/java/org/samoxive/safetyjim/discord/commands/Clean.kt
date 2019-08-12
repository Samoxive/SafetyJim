package org.samoxive.safetyjim.discord.commands

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhard
import org.samoxive.safetyjim.tryhardAsync
import java.util.*

class Clean : Command() {
    override val usages = arrayOf("clean <number> - deletes last number of messages", "clean <number> @user - deletes number of messages from specified user", "clean <number> bot - deletes number of messages sent from bots")

    private suspend fun fetchMessages(channel: TextChannel, messageCount: Int, skipOneMessage: Boolean, filterBotMessages: Boolean, filterUserMessages: Boolean, filterUser: User?): List<Message> {
        var messageCount = messageCount
        if (skipOneMessage) {
            messageCount = if (messageCount == 100) 100 else messageCount + 1
        }

        // if we want to delete bot messages, we want to find as much as we can and then only delete the amount we need
        // if not, we just pass the messageCount back, same story with user messages
        var messages: MutableList<Message> = channel.history.retrievePast(if (filterBotMessages || filterUserMessages) 100 else messageCount).await()

        if (skipOneMessage) {
            tryhard { messages.removeAt(0) }
        }

        if (filterBotMessages) {
            val tempMessages = ArrayList<Message>()
            var iterationCount = 0

            for (message in messages) {
                if (iterationCount == messageCount) {
                    break
                } else {
                    if (message.author.isBot) {
                        tempMessages.add(message)
                        iterationCount++
                    }
                }
            }

            messages = tempMessages
        }

        if (filterUserMessages) {
            val tempMessages = ArrayList<Message>()
            messageCount--
            var iterationCount = 0

            for (message in messages) {
                if (iterationCount == messageCount) {
                    break
                } else {
                    if (message.author.id == filterUser!!.id) {
                        tempMessages.add(message)
                        iterationCount++
                    }
                }
            }

            messages = tempMessages
        }

        return messages
    }

    private fun seperateMessages(messages: List<Message>): Pair<List<Message>, List<Message>> {
        val oldMessages = ArrayList<Message>()
        val newMessages = ArrayList<Message>()
        val now = Date().time / 1000

        for (message in messages) {
            if (now - message.timeCreated.toEpochSecond() <= 60 * 60 * 24 * 12) {
                newMessages.add(message)
            } else {
                oldMessages.add(message)
            }
        }

        return oldMessages to newMessages
    }

    private suspend fun bulkDelete(messages: Pair<List<Message>, List<Message>>, channel: TextChannel) {
        val (oldMessages, newMessages) = messages
        val futures = ArrayList<Deferred<Void>>()

        if (newMessages.size in 2..100) {
            channel.deleteMessages(newMessages).await()
        } else {
            for (message in newMessages) {
                futures.add(GlobalScope.async { message.delete().await() })
            }
        }

        for (message in oldMessages) {
            futures.add(GlobalScope.async { message.delete().await() })
        }

        futures.awaitAll()
    }

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member!!
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            message.failMessage("You don't have enough permissions to execute this command! Required permission: Manage Messages")
            return false
        }

        if (!selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            message.failMessage("I don't have enough permissions to do that! Required permission: Manage Messages, Read Message History")
            return false
        }

        if (!messageIterator.hasNextInt()) {
            message.failReact()
            return true
        }

        val messageCount = messageIterator.nextInt()

        if (messageCount < 1) {
            message.failMessage("You can't delete zero or negative messages.")
            return false
        } else if (messageCount > 100) {
            message.failMessage("You can't delete more than 100 messages at once.")
            return false
        }

        val messages = if (!messageIterator.hasNext()) {
            fetchMessages(channel, messageCount, true, false, false, null)
        } else {
            if (messageIterator.hasNext("bot")) {
                fetchMessages(channel, messageCount, false, true, false, null)
            } else {
                val (searchResult, user) = messageIterator.findUser(message, true)
                if (searchResult == SearchUserResult.NOT_FOUND || user == null) {
                    message.failMessage("Invalid target, please try mentioning a user or writing `bot`.")
                    return false
                }

                val messages = fetchMessages(channel, messageCount, true, false, true, user)
                if (searchResult == SearchUserResult.GUESSED) {
                    message.askConfirmation(bot, user) ?: return false
                }

                messages
            }
        }

        val seperatedMessages = seperateMessages(messages)
        tryhardAsync { bulkDelete(seperatedMessages, channel) }

        message.successReact()

        return false
    }
}
