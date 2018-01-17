package org.samoxive.safetyjim.server.entries;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.samoxive.jooq.generated.Tables;
import org.samoxive.jooq.generated.tables.Messages;
import org.samoxive.jooq.generated.tables.records.MessagesRecord;
import org.samoxive.safetyjim.helpers.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Stat {
    public int date;
    public int count;

    private Stat(int date, int count) {
        this.date = date;
        this.count = count;
    }

    private static List<Stat> fromRecords(Result<MessagesRecord> records, int interval) {
        HashMap<Long, Integer> group = new HashMap<>();
        for (MessagesRecord record: records) {
            // reduce accuracy of date to get unique ids of records by interval
            long date = record.getDate() / (1000 * interval);
            group.put(date, group.getOrDefault(date, 0) + 1);
        }

        List<Stat> counts = group.entrySet()
                .stream()
                .map((entry) -> new Pair<>(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing((e) -> e.getLeft()))
                .map((pair) -> new Stat(pair.getLeft().intValue() * interval, pair.getRight())) // revert accuracy reduction to get the actual unix epoch in seconds
                .collect(Collectors.toList());

        if (counts.size() < 2) {
            // There isn't any data we can fill in
            return counts;
        }

        Stat first = counts.get(0);
        Stat last = counts.get(counts.size() - 1);

        int window = last.date - first.date;
        int count = (window / interval) + 1;
        List<Stat> result = new ArrayList<>(count);
        int k = 0;
        for (int i = 0; i < count; i++) {
            if (counts.get(k).date == first.date + i * interval) {
                result.add(counts.get(k));
                k++;
            } else {
                result.add(null);
            }
        }


        return result;

    }

    public static List<Stat> getGuildMessageStats(DSLContext database, String guildId, long from, long to, int interval) {
        Result<MessagesRecord> records = database.selectFrom(Tables.MESSAGES)
                                                 .where(Tables.MESSAGES.GUILDID.eq(guildId))
                                                 .and(Tables.MESSAGES.DATE.between(from, to))
                                                 .orderBy(Tables.MESSAGES.DATE.asc())
                                                 .fetch();

        return fromRecords(records, interval);
    }

    public static List<Stat> getChannelMessageStats(DSLContext database, String guildId, String channelId, long from, long to, int interval) {
        Result<MessagesRecord> records = database.selectFrom(Tables.MESSAGES)
                                                 .where(Tables.MESSAGES.GUILDID.eq(guildId))
                                                 .and(Tables.MESSAGES.CHANNELID.eq(channelId))
                                                 .and(Tables.MESSAGES.DATE.between(from, to))
                                                 .orderBy(Tables.MESSAGES.DATE.asc())
                                                 .fetch();

        return fromRecords(records, interval);
    }
}
