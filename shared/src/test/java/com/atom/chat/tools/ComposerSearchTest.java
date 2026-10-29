package com.atom.chat.tools;

import com.atom.chat.chat.ChatMessage;
import com.atom.chat.chat.LocalEcho;
import com.atom.chat.text.RichText;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ComposerSearchTest {
    private static class Host implements ComposerSearch.Host {
        String text = "original draft"; int caret = 3; Object scope = "public";
        List<ChatMessage> messages = List.of(); ChatMessage selected;
        public String composerText() { return text; }
        public int composerCaret() { return caret; }
        public void setComposerText(String text) { this.text = text; }
        public void moveComposerCaret(int caret) { this.caret = caret; }
        public void focusComposer() { }
        public Object searchScope() { return scope; }
        public List<ChatMessage> searchMessages() { return messages; }
        public boolean revealSearchMessage(ChatMessage m) { selected = m; return messages.contains(m); }
        public void highlightSearchMessage(ChatMessage m) { selected = m; }
    }
    private static ChatMessage message(String text) { return LocalEcho.build(text, text, null, null, null, "Alice", RichText.empty()); }
    @Test void usesTheRealFieldAndRestoresItsDraftAndCaret() {
        var host = new Host(); var search = new ComposerSearch(host);
        search.open(); assertEquals("", host.text); assertEquals(0, host.caret);
        host.text = "世界"; search.update(); assertEquals(0, search.count());
        search.close(); assertEquals("original draft", host.text); assertEquals(3, host.caret);
        search.close(); assertEquals("original draft", host.text);
    }
    @Test void onlySearchesSuppliedCurrentPageAndCyclesWithoutReopeningPages() {
        var host = new Host(); var a = message("第一条建造"); var b = message("第二条建造");
        host.messages = List.of(a, b); var search = new ComposerSearch(host);
        search.open(); host.text = "建造"; search.update();
        assertEquals(2, search.count()); ChatMessage first = host.selected;
        search.move(1); assertNotSame(first, host.selected);
        search.move(1); assertSame(first, host.selected);
        host.messages = List.of(a); search.update(); assertEquals(1, search.count()); assertSame(a, host.selected);
        host.scope = "private-Bob"; search.update(); assertFalse(search.active());
        assertEquals("original draft", host.text); assertNull(host.selected);
    }
    @Test void anInFlightUploadCompletesIntoTheParkedDraft() {
        var host = new Host(); var search = new ComposerSearch(host); search.open();
        host.text = "query";
        assertTrue(search.appendToParkedDraft(" [image:abc]")); assertEquals("query", host.text);
        search.close(); assertEquals("original draft [image:abc]", host.text); assertEquals(3, host.caret);
        assertFalse(search.appendToParkedDraft("late"));
    }
}
