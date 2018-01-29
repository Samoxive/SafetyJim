package org.samoxive.safetyjim.discord;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.samoxive.safetyjim.helpers.Pair;

import java.util.Date;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class TextUtils {
    public static String truncateForEmbed(String s) {
        if (s.length() < 1024) {
            return s;
        } else {
            return s.substring(0, 1021) + "...";
        }
    }

    public static String seekScannerToEnd(Scanner scan) {
        StringBuilder data = new StringBuilder();

        while (scan.hasNextLine()) {
            data.append(scan.nextLine());
            data.append("\n");
        }

        return data.toString().trim();
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
    public static Pair<String, Date> getTextAndTime(Scanner scan) throws InvalidTimeInputException, TimeInputInPastException {
        String text;
        String timeArgument = null;

        String[] splitArgumentsRaw = seekScannerToEnd(scan).split("\\|");

        if (splitArgumentsRaw.length == 1) {
            text = splitArgumentsRaw[0];
        } else {
            text = splitArgumentsRaw[0];
            timeArgument = splitArgumentsRaw[1];
        }

        text = text.trim();
        timeArgument = timeArgument == null ? null: timeArgument.trim();

        Date time = null;
        Date now = new Date();

        if (timeArgument != null) {
            Parser parser = new Parser();
            List<DateGroup> dateGroups = parser.parse(timeArgument);

            try {
                time = dateGroups.get(0).getDates().get(0);
            } catch (IndexOutOfBoundsException e) {
                throw new InvalidTimeInputException();
            }

            if (time.compareTo(now) < 0) {
                throw new TimeInputInPastException();
            }
        }

        return new Pair<>(text, time);
    }

    private static String nextPattern(Scanner scan, Pattern pattern) {
        if (scan.hasNext(pattern)) {
            return scan.next(pattern);
        } else {
            return null;
        }
    }

    public static String nextUserMention(Scanner scan) {
        return nextPattern(scan, DiscordUtils.USER_MENTION_PATTERN);
    }

    public static String nextChannelMention(Scanner scan) {
        return nextPattern(scan, DiscordUtils.CHANNEL_MENTION_PATTERN);
    }

    public static String nextRoleMention(Scanner scan) {
        return nextPattern(scan, DiscordUtils.ROLE_MENTION_PATTERN);
    }

    public static class InvalidTimeInputException extends Exception {}
    public static class TimeInputInPastException extends Exception {}
}
