package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.User
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction
import org.samoxive.safetyjim.discord.*
import org.samoxive.safetyjim.tryhard
import java.util.*

class Clean : Command() {
    override val usages = arrayOf("clean <number> - deletes last number of messages", "clean <number> @user - deletes number of messages from specified user", "clean <number> bot - deletes number of messages sent from bots")

    private fun fetchMessages(channel: TextChannel, messageCount: Int, skipOneMessage: Boolean, filterBotMessages: Boolean, filterUserMessages: Boolean, filterUser: User?): List<Message> {
        var messageCount = messageCount
        if (skipOneMessage) {
            messageCount = if (messageCount == 100) 100 else messageCount + 1
        }

        // if we want to delete bot messages, we want to find as much as we can and then only delete the amount we need
        // if not, we just pass the messageCount back, same story with user messages
        var messages: MutableList<Message> = channel.history.retrievePast(if (filterBotMessages || filterUserMessages) 100 else messageCount).complete()

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
            if (now - message.creationTime.toEpochSecond() <= 60 * 60 * 24 * 12) {
                newMessages.add(message)
            } else {
                oldMessages.add(message)
            }
        }

        return oldMessages to newMessages
    }

    private fun bulkDelete(messages: Pair<List<Message>, List<Message>>, channel: TextChannel) {
        val (oldMessages, newMessages) = messages
        val futures = ArrayList<AuditableRestAction<Void>>()

        if (newMessages.size in 2..100) {
            channel.deleteMessages(newMessages).complete()
        } else {
            for (message in newMessages) {
                futures.add(message.delete())
            }
        }

        for (message in oldMessages) {
            futures.add(message.delete())
        }

        for (future in futures) {
            future.complete()
        }
    }

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)

        val member = event.member
        val message = event.message
        val channel = event.channel
        val guild = event.guild
        val selfMember = guild.selfMember

        if (!member.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            message.failMessage(bot, "You don't have enough permissions to execute this command! Required permission: Manage Messages")
            return false
        }

        if (!selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            message.failMessage(bot, "I don't have enough permissions to do that! Required permission: Manage Messages, Read Message History")
            return false
        }

        if (!messageIterator.hasNextInt()) {
            message.failReact(bot)
            return true
        }

        val messageCount = messageIterator.nextInt()

        if (messageCount < 1) {
            message.failMessage(bot, "You can't delete zero or negative messages.")
            return false
        } else if (messageCount > 100) {
            message.failMessage(bot, "You can't delete more than 100 messages at once.")
            return false
        }

        val messages = if (!messageIterator.hasNext()) {
            fetchMessages(channel, messageCount, true, false, false, null)
        } else {
            if (messageIterator.hasNext("bot")) {
                fetchMessages(channel, messageCount, false, true, false, null)
            } else {
                val (searchResult, user) = messageIterator.findUser(message, true)
                val messages = fetchMessages(channel, messageCount, true, false, true, user)
                if (searchResult == SearchUserResult.NOT_FOUND || user == null) {
                    message.failMessage(bot, "Invalid target, please try mentioning a user or writing `bot`.")
                    return false
                }

                if (searchResult == SearchUserResult.GUESSED) {
                    message.askConfirmation(bot, user) ?: return false
                }

                messages
            }
        }

        val seperatedMessages = seperateMessages(messages)
        tryhard { bulkDelete(seperatedMessages, channel) }

        message.successReact(bot)

        return false
    }
}
