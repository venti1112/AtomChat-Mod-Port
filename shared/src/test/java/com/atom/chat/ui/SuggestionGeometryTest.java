package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SuggestionGeometryTest {
    @Test void outsideLeftEdgeAlignsWithComposerAndToolsInsteadOfTheTextCaret() {
        for (boolean docked : new boolean[]{false, true}) {
            var layout = UiLayout.of(15, 15, 420, 780, 0, 0, docked);
            for (float textWidth : new float[]{0, 180, 1000}) {
                var popup = SuggestionGeometry.of(layout, 10, textWidth);
                assertEquals(layout.inputBar.x(), popup.bounds().x());
                assertEquals(com.atom.chat.tools.ToolLayout.menu(layout).x(), popup.bounds().x());
                assertTrue(popup.bounds().right() <= layout.inputBar.right());
            }
        }
    }
    @Test void clampsToPanelAndHitTestingUsesThePaintedRows() {
        var layout = UiLayout.of(0, 0, 350, 700, 0, 0);
        var popup = SuggestionGeometry.of(layout, 40, 1000);
        assertTrue(layout.rect().contains(popup.bounds()));
        assertTrue(popup.bounds().bottom() < layout.inputBar.y());
        assertTrue(popup.rows() <= 10);
        for (int row = 0; row < popup.rows(); row++) {
            var box = popup.row(row);
            assertEquals(row, popup.hit(box.x() + box.w() / 2, box.y() + box.h() / 2));
        }
        assertEquals(-1, popup.hit(popup.bounds().right() + 1, popup.bounds().y()));
    }
}
