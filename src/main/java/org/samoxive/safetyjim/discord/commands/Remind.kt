package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.jetbrains.exposed.sql.transactions.transaction
import org.samoxive.safetyjim.database.JimReminder
import org.samoxive.safetyjim.discord.Command
import org.samoxive.safetyjim.discord.DiscordBot
import org.samoxive.safetyjim.discord.DiscordUtils
import org.samoxive.safetyjim.discord.TextUtils
import java.util.*

class Remind : Command() {
    override val usages = arrayOf("remind message - sets a timer to remind you a message in a day", "remind message | time - sets a timer to remind you a message in specified time period")

    override fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, args: String): Boolean {
        val messageIterator = Scanner(args)

        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild

        val parsedReminderAndTime = try {
            TextUtils.getTextAndTime(messageIterator)
        } catch (e: TextUtils.InvalidTimeInputException) {
            DiscordUtils.failMessage(bot, message, "Invalid time argument. Please try again.")
            return false
        } catch (e: TextUtils.TimeInputInPastException) {
            DiscordUtils.failMessage(bot, message, "Your time argument was set for the past. Try again.\n" + "If you're specifying a date, e.g. `30 December`, make sure you also write the year.")
            return false
        }

        var (reminder, remindTime) = parsedReminderAndTime

        if (reminder == "") {
            return true
        }

        val now = Date().time
        remindTime = remindTime ?: Date(now + 1000 * 60 * 60 * 24)

        transaction {
            JimReminder.new {
                userid = user.id
                channelid = channel.id
                guildid = guild.id
                createtime = now / 1000
                remindtime = remindTime.time / 1000
                this.message = reminder
                reminded = false
            }
        }

        DiscordUtils.successReact(bot, message)

        return false
    }
}
