package com.atom.chat.tools;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searches the retained conversation snapshots, including rows hidden by folds. */
public final class HistorySearch {
    public enum Channel { ALL, PUBLIC, PRIVATE }
    public record Row<T>(T message, String peer, long timestamp, String sender, String text) { }

    private HistorySearch() { }

    public static <T> List<Row<T>> find(List<Row<T>> rows, String query, Channel channel) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return List.of();
        return rows.stream().filter(row -> channel == Channel.ALL
                        || (channel == Channel.PRIVATE) == (row.peer() != null))
                .filter(row -> contains(row.text(), needle) || contains(row.sender(), needle))
                .sorted(Comparator.comparingLong((Row<T> row) -> row.timestamp()).reversed())
                .toList();
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }
}
