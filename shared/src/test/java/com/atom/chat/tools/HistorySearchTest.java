package com.atom.chat.tools;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistorySearchTest {
    @Test void searchesFoldedRowsAndSeparatesChannelsInNewestOrder() {
        var old = new HistorySearch.Row<>("old", null, 1, "Alice", "早期建造记录");
        var newer = new HistorySearch.Row<>("new", "Bob", 2, "Bob", "建造材料");
        var rows = List.of(old, newer);
        assertEquals(List.of(newer, old), HistorySearch.find(rows, "建造", HistorySearch.Channel.ALL));
        assertEquals(List.of(old), HistorySearch.find(rows, "建造", HistorySearch.Channel.PUBLIC));
        assertEquals(List.of(newer), HistorySearch.find(rows, "建造", HistorySearch.Channel.PRIVATE));
    }
    @Test void matchesCaseInsensitivelyWithoutTreatingInputAsRegex() {
        var row = new HistorySearch.Row<>("id", null, 1, "ALIce", "hello [world]");
        assertEquals(List.of(row), HistorySearch.find(List.of(row), "alice", HistorySearch.Channel.ALL));
        assertEquals(List.of(row), HistorySearch.find(List.of(row), "[world]", HistorySearch.Channel.ALL));
        assertTrue(HistorySearch.find(List.of(row), "   ", HistorySearch.Channel.ALL).isEmpty());
    }
}
