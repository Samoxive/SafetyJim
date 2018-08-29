package org.samoxive.safetyjim.discord

import com.joestelmach.natty.Parser

import java.util.Date
import java.util.Scanner
import java.util.regex.Pattern

object TextUtils {
    fun truncateForEmbed(s: String): String {
        return if (s.length < 1024) {
            s
        } else {
            s.substring(0, 1021) + "..."
        }
    }

    fun seekScannerToEnd(scan: Scanner): String {
        val data = StringBuilder()

        while (scan.hasNextLine()) {
            data.append(scan.nextLine())
            data.append("\n")
        }

        return data.toString().trim { it <= ' ' }
    }

    /**
     * Parses command arguments and returns a text and a date provided that
     * the argument is in the form of "text | human time", the date argument
     * has to be in the future
     * @param scan
     * @return
     * @throws InvalidTimeInputException
     * @throws TimeInputInPastException
     */
    @Throws(InvalidTimeInputException::class, TimeInputInPastException::class)
    fun getTextAndTime(scan: Scanner): Pair<String, Date?> {
        var text: String
        var timeArgument: String? = null

        val splitArgumentsRaw = seekScannerToEnd(scan).split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (splitArgumentsRaw.size == 1) {
            text = splitArgumentsRaw[0]
        } else {
            text = splitArgumentsRaw[0]
            timeArgument = splitArgumentsRaw[1]
        }

        text = text.trim { it <= ' ' }
        timeArgument = timeArgument?.trim { it <= ' ' }

        var time: Date? = null
        val now = Date()

        if (timeArgument != null) {
            val parser = Parser()
            val dateGroups = parser.parse(timeArgument)

            try {
                time = dateGroups[0].dates[0]!!
            } catch (e: IndexOutOfBoundsException) {
                throw InvalidTimeInputException()
            }

            if (time < now) {
                throw TimeInputInPastException()
            }
        }

        return text to time
    }

    private fun nextPattern(scan: Scanner, pattern: Pattern): String? {
        return if (scan.hasNext(pattern)) {
            scan.next(pattern)
        } else {
            null
        }
    }

    fun nextUserMention(scan: Scanner): String? {
        return nextPattern(scan, DiscordUtils.USER_MENTION_PATTERN)
    }

    fun nextChannelMention(scan: Scanner): String? {
        return nextPattern(scan, DiscordUtils.CHANNEL_MENTION_PATTERN)
    }

    fun nextRoleMention(scan: Scanner): String? {
        return nextPattern(scan, DiscordUtils.ROLE_MENTION_PATTERN)
    }

    class InvalidTimeInputException : Exception()
    class TimeInputInPastException : Exception()
}
