package com.atom.chat.ui;

import com.atom.chat.tools.ComposerTools;
import com.atom.chat.tools.ToolLayout;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolMenuInteractionTest {
    @org.junit.jupiter.api.BeforeAll static void setup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        UiFeatureRenderTest.setup(root);
    }
    @Test void openingClosingAndHoverRemainOnThePopupTimeline() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            var canvas = surface.getCanvas();
            tools.toggle();
            canvas.clear(0); tools.render(canvas, -1, -1, 1);
            var early = pixels(surface);
            canvas.clear(0); tools.render(canvas, -1, -1, 200);
            assertFalse(java.util.Arrays.equals(early, pixels(surface)), "opening must fade and scale like sibling popups");
            var row = ToolLayout.menu(host.layout);
            canvas.clear(0); tools.render(canvas, row.x() + 40, row.y() + 20, 1);
            var hoverEarly = pixels(surface);
            canvas.clear(0); tools.render(canvas, row.x() + 40, row.y() + 20, 90);
            assertFalse(java.util.Arrays.equals(hoverEarly, pixels(surface)), "hover must fade and bounce, not jump to a flat wash");
            tools.onClick(-20, -20, 0);
            canvas.clear(0); tools.render(canvas, -1, -1, 1);
            assertTrue(nonzero(pixels(surface)), "closing must keep painting the outgoing menu");
            canvas.clear(0); tools.render(canvas, -1, -1, 200);
            assertFalse(nonzero(pixels(surface)), "outgoing popup must release its surface when closed");
        }
    }
    @Test void playersReplaceTheMenuAndDoNotTakeOverTheConversation() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        tools.toggle();
        var bounds = ToolLayout.menu(host.layout);
        tools.onClick(bounds.x() + 30, bounds.y() + ToolLayout.MENU_ROW_H * 1.5, 0);
        try (var surface = Surface.makeRasterN32Premul(460, 820)) {
            surface.getCanvas().clear(0); tools.render(surface.getCanvas(), -1, -1, 200);
            var bytes = pixels(surface);
            int x = (int) (host.layout.header.x() + 20), y = (int) (host.layout.header.y() + 20);
            assertEquals(0, bytes[(y * 460 + x) * 4 + 3], "the tools popup must not replace the conversation header");
        }
        assertTrue(tools.active());
    }
    @Test void searchBorrowsNativeTypingAndConsumesEverySendAction() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        tools.toggle(); var bounds = ToolLayout.menu(host.layout);
        tools.onClick(bounds.x() + 30, bounds.y() + ToolLayout.MENU_ROW_H / 2, 0);
        assertTrue(tools.searching()); assertEquals("", host.text);
        assertFalse(tools.onChar('中', 0));
        host.text = "中文关键词"; tools.update();
        assertTrue(tools.onKey(257, 0, 0)); assertEquals("中文关键词", host.text);
        assertTrue(tools.onClick(host.layout.sendBtn.x() + 5, host.layout.sendBtn.y() + 5, 0));
        assertEquals(0, host.picks);
        assertTrue(tools.onKey(256, 0, 0)); assertFalse(tools.searching());
        assertEquals("draft", host.text); assertEquals(2, host.caret);
    }
    @Test void openingAnotherComposerBorrowRestoresTheDraftFirst() {
        var host = new UiFeatureRenderTest.Host(); var tools = new ComposerTools(host);
        tools.toggle(); var bounds = ToolLayout.menu(host.layout);
        tools.onClick(bounds.x() + 30, bounds.y() + ToolLayout.MENU_ROW_H / 2, 0);
        host.text = "query";
        assertFalse(tools.onClick(host.layout.phraseBtn.x() + 5, host.layout.phraseBtn.y() + 5, 0));
        assertFalse(tools.searching()); assertEquals("draft", host.text);
    }
    private static byte[] pixels(Surface surface) {
        try (var bitmap = new io.github.humbleui.skija.Bitmap()) {
            bitmap.allocN32Pixels(460, 820); assertTrue(surface.readPixels(bitmap, 0, 0)); return bitmap.readPixels();
        }
    }
    private static boolean nonzero(byte[] pixels) { for (byte b : pixels) if (b != 0) return true; return false; }
}
