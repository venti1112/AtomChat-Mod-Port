package com.atom.chat.ui;

import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Bitmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClickRipplesTest {
    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }
    @Test void addingAMenuDoesNotEraseTheTriggerRippleAndRemovedControlsDoNotSplashThePage() {
        var feedback = new ClickRipples(); Object screen = new Object();
        try (var surface = Surface.makeRasterN32Premul(120, 120)) {
            var canvas = surface.getCanvas(); canvas.clear(0xFF202020);
            feedback.begin(screen); ClickRipples.region(10, 70, 40, 40, 8, 0xFF202020); feedback.end(canvas, 16);
            feedback.click(30, 90);
            feedback.begin(screen); ClickRipples.region(10, 70, 40, 40, 8, 0xFF202020);
            ClickRipples.block(new UiLayout.Rect(10, 10, 80, 50)); feedback.end(canvas, 100);
            byte[] lit = pixels(surface);
            assertNotEquals(32, Byte.toUnsignedInt(lit[(90 * 120 + 30) * 4]));
            canvas.clear(0xFF202020);
            feedback.begin(screen); feedback.end(canvas, 16);
            assertEquals(32, Byte.toUnsignedInt(pixels(surface)[(90 * 120 + 30) * 4]));
            feedback.clear();
        }
    }
    private static byte[] pixels(Surface surface) {
        try (var bitmap = new Bitmap()) {
            bitmap.allocN32Pixels(120, 120); assertTrue(surface.readPixels(bitmap, 0, 0)); return bitmap.readPixels();
        }
    }
}
