package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.requests.restaction.AuditableRestAction
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils

import java.util.ArrayList
import java.util.Date
import java.util.Scanner

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
            try {
                messages.removeAt(0)
            } catch (e: IndexOutOfBoundsException) {
                // we just want to remove first element, ignore if list is empty
            }

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
        val (newMessages, oldMessage) = messages
        val futures = ArrayList<AuditableRestAction<Void>>()

        if (newMessages.size in 2..100) {
            channel.deleteMessages(newMessages).complete()
        } else {
            for (message in newMessages) {
                futures.add(message.delete())
            }
        }

        for (message in oldMessage) {
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
            DiscordUtils.failMessage(bot, message, "You don't have enough permissions to execute this command! Required permission: Manage Messages")
            return false
        }

        if (!selfMember.hasPermission(channel, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            DiscordUtils.failMessage(bot, message, "I don't have enough permissions to do that! Required permission: Manage Messages, Read Message History")
            return false
        }

        if (!messageIterator.hasNextInt()) {
            DiscordUtils.failReact(bot, message)
            return true
        }

        val messageCount = messageIterator.nextInt()

        if (messageCount < 1) {
            DiscordUtils.failMessage(bot, message, "You can't delete zero or negative messages.")
            return false
        } else if (messageCount > 100) {
            DiscordUtils.failMessage(bot, message, "You can't delete more than 100 messages at once.")
            return false
        }

        val targetArgument: String
        var targetUser: User? = null

        if (!messageIterator.hasNext()) {
            targetArgument = ""
        } else if (messageIterator.hasNext(DiscordUtils.USER_MENTION_PATTERN)) {
            val mentionedUsers = message.mentionedUsers
            if (mentionedUsers.isEmpty()) {
                DiscordUtils.failMessage(bot, message, "Could not find the user to clean messages of!")
                return false
            }
            targetUser = mentionedUsers[0]
            targetArgument = "user"
        } else {
            targetArgument = messageIterator.next()
        }

        val messages = when (targetArgument) {
            "" -> fetchMessages(channel, messageCount, true, false, false, null)
            "bot" -> fetchMessages(channel, messageCount, false, true, false, null)
            "user" -> {
                if (targetUser != null) {
                    fetchMessages(channel, messageCount, true, false, true, targetUser)
                } else {
                    DiscordUtils.failMessage(bot, message, "Invalid target, please try mentioning a user or writing `bot`.")
                    return false
                }
            }
            else -> {
                DiscordUtils.failMessage(bot, message, "Invalid target, please try mentioning a user or writing `bot`.")
                return false
            }
        }

        val seperatedMessages = seperateMessages(messages)
        try {
            bulkDelete(seperatedMessages, channel)
        } catch (e: Exception) {
            //
        }

        DiscordUtils.successReact(bot, message)

        return false
    }
}
