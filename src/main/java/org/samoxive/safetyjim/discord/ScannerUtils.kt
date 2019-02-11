package org.samoxive.safetyjim.discord

import com.joestelmach.natty.Parser
import me.xdrop.fuzzywuzzy.FuzzySearch
import net.dv8tion.jda.core.entities.Message
import net.dv8tion.jda.core.entities.User
import java.util.*
import java.util.regex.Pattern

val USER_MENTION_REGEX = Regex("<@!?([0-9]+)>")
val CHANNEL_MENTION_REGEX = Regex("<#!?([0-9]+)>")
val CHANNEL_MENTION_PATTERN: Pattern = CHANNEL_MENTION_REGEX.toPattern()

fun truncateForEmbed(s: String): String {
    return if (s.length < 1024) {
        s
    } else {
        s.substring(0, 1021) + "..."
    }
}

fun Scanner.seekToEnd(): String {
    val data = StringBuilder()

    while (this.hasNextLine()) {
        data.append(this.nextLine())
        data.append("\n")
    }

    return data.toString().trim()
}

/**
 * Parses command arguments and returns a text and a date provided that
 * the argument is in the form of "text | human time", the date argument
 * has to be in the future
 * @return
 * @throws InvalidTimeInputException
 * @throws TimeInputInPastException
 */
@Throws(InvalidTimeInputException::class, TimeInputInPastException::class)
fun Scanner.getTextAndTime(): Pair<String, Date?> {
    var text: String
    var timeArgument: String? = null

    val splitArgumentsRaw = this.seekToEnd().split("|")

    if (splitArgumentsRaw.size == 1) {
        text = splitArgumentsRaw[0]
    } else {
        text = splitArgumentsRaw[0]
        timeArgument = splitArgumentsRaw[1]
    }

    text = text.trim()
    timeArgument = timeArgument?.trim()

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

enum class SearchUserResult {
    NOT_FOUND, EXACT, GUESSED
}

suspend fun Scanner.findUser(message: Message, isForBan: Boolean = false): Pair<SearchUserResult, User?> {
    val jda = message.jda
    val guild = message.guild
    val input = if (this.hasNext()) {
        this.next()
    } else {
        return SearchUserResult.NOT_FOUND to null
    }

    val mentionMatch = USER_MENTION_REGEX.matchEntire(input)
    if (mentionMatch != null) {
        val userId = mentionMatch.groupValues[1].toLong()
        val member = guild.getMemberById(userId)
        return if (member != null) {
            SearchUserResult.EXACT to member.user
        } else {
            if (isForBan) {
                val user = jda.retrieveUserById(userId).tryAwait()
                (if (user != null) SearchUserResult.EXACT else SearchUserResult.NOT_FOUND) to user
            } else {
                SearchUserResult.NOT_FOUND to null
            }
        }
    }

    val userId = input.toLongOrNull()
    if (userId != null) {
        val member = guild.getMemberById(userId)
        if (member != null) {
            return SearchUserResult.EXACT to member.user
        } else {
            if (isForBan) {
                val user = jda.retrieveUserById(userId).tryAwait()
                if (user != null) {
                    return SearchUserResult.EXACT to user
                }
            }
        }
    }

    val members = guild.members
    val usernamesAndNicknames = members.asSequence().map { it.effectiveName }.plus(members.map { it.user.name }).toList()
    val search = FuzzySearch.extractOne(input, usernamesAndNicknames)
    val user = members[search.index % members.size].user

    return if (search.score >= 75) SearchUserResult.GUESSED to user else SearchUserResult.NOT_FOUND to null
}

suspend fun Scanner.findBannedUser(message: Message): Pair<SearchUserResult, User?> {
    val guild = message.guild
    val input = if (this.hasNext()) {
        this.next()
    } else {
        return SearchUserResult.NOT_FOUND to null
    }

    val banList = guild.banList.await().map { it.user }
    if (banList.isEmpty()) {
        return SearchUserResult.NOT_FOUND to null
    }

    val mentionMatch = USER_MENTION_REGEX.matchEntire(input)
    if (mentionMatch != null) {
        val userId = mentionMatch.groupValues[1].toLong()
        val user = banList.firstOrNull { it.idLong == userId }
        return if (user != null) {
            SearchUserResult.EXACT to user
        } else {
            SearchUserResult.NOT_FOUND to null
        }
    }

    val userId = input.toLongOrNull()
    if (userId != null) {
        val user = banList.firstOrNull { it.idLong == userId }
        if (user != null) {
            return SearchUserResult.EXACT to user
        }
    }

    val search = FuzzySearch.extractOne(input, banList.map { it.name })
    val user = banList[search.index]
    return if (search.score >= 75) SearchUserResult.GUESSED to user else SearchUserResult.NOT_FOUND to null
}

class InvalidTimeInputException : Exception()
class TimeInputInPastException : Exception()
