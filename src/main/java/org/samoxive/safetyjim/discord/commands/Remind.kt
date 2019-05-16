package org.samoxive.safetyjim.discord.commands

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import org.samoxive.safetyjim.database.ReminderEntity
import org.samoxive.safetyjim.database.RemindersTable
import org.samoxive.safetyjim.database.SettingsEntity
import org.samoxive.safetyjim.discord.*
import java.util.*

class Remind : Command() {
    override val usages = arrayOf("remind message - sets a timer to remind you a message in a day", "remind message | time - sets a timer to remind you a message in specified time period")

    override suspend fun run(bot: DiscordBot, event: GuildMessageReceivedEvent, settings: SettingsEntity, args: String): Boolean {
        val messageIterator = Scanner(args)

        val user = event.author
        val message = event.message
        val channel = event.channel
        val guild = event.guild

        if (args.isEmpty()) {
            return true
        }

        val parsedReminderAndTime = try {
            messageIterator.getTextAndTime()
        } catch (e: InvalidTimeInputException) {
            message.failMessage("Invalid time argument. Please try again.")
            return false
        } catch (e: TimeInputInPastException) {
            message.failMessage("Your time argument was set for the past. Try again.\n" + "If you're specifying a date, e.g. `30 December`, make sure you also write the year.")
            return false
        }

        var (reminder, remindTime) = parsedReminderAndTime

        if (reminder == "") {
            return true
        }

        val now = Date().time
        remindTime = remindTime ?: Date(now + 1000 * 60 * 60 * 24)

        RemindersTable.insertReminder(
                ReminderEntity(
                        userId = user.idLong,
                        channelId = channel.idLong,
                        guildId = guild.idLong,
                        createTime = now / 1000,
                        remindTime = remindTime.time / 1000,
                        message = reminder,
                        reminded = false
                )
        )

        message.successReact()

        return false
    }
}
