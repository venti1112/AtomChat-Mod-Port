package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import io.github.humbleui.skija.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EmojiGlyphCacheTest {
    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir() { return root.resolve("config"); }
            public Path gameDir() { return root.resolve("game"); }
            public boolean isModLoaded(String id) { return false; }
        });
    }
    @BeforeEach void clear() { SkiaFontRenderer.clearEmojiGlyphCache(); }
    @Test void animatedScaleReusesTheSameRasterizedGlyph() {
        try (var surface = Surface.makeRasterN32Premul(500, 500)) {
            var canvas = surface.getCanvas(); var font = FontManager.font(27.5F);
            for (int i = 0; i < 25; i++) {
                canvas.save(); float scale = 1.48F * (.92F + .08F * i / 24);
                canvas.scale(scale, scale); SkiaFontRenderer.drawText(canvas, font, "😀", 30, 70, 0xFFFFFFFF); canvas.restore();
            }
            assertEquals(1, SkiaFontRenderer.emojiGlyphBuilds(), "animated transforms must sample one bitmap, not repaint the colour font");
        }
    }
    @Test void cachedOpaqueGlyphsMatchOriginalRasterPixelsAndAdvances() throws Exception {
        var primary = FontManager.font(27.5F);
        var resolve = SkiaFontRenderer.class.getDeclaredMethod("fontFor", Font.class, int.class); resolve.setAccessible(true);
        for (String text : new String[]{"😀", "❤", "⬆", "😀😀"}) {
            try (var expected = Surface.makeRasterN32Premul(400, 300); var actual = Surface.makeRasterN32Premul(400, 300);
                 var paint = new Paint().setColor(0xFFFFFFFF)) {
                var font = (Font) resolve.invoke(null, primary, text.codePointAt(0));
                expected.getCanvas().clear(0); actual.getCanvas().clear(0);
                expected.getCanvas().scale(2, 2); actual.getCanvas().scale(2, 2);
                expected.getCanvas().drawString(text, 20, 60, font, paint);
                float width = SkiaFontRenderer.getStringWidth(primary, text);
                SkiaFontRenderer.drawText(actual.getCanvas(), primary, text, 20, 60, 0xFFFFFFFF);
                assertArrayEquals(pixels(expected, 400, 300), pixels(actual, 400, 300), text);
                assertEquals(width, SkiaFontRenderer.getStringWidth(primary, text));
            }
        }
        assertTrue(SkiaFontRenderer.emojiGlyphBuilds() > 0);
    }
    @Test void boundsNativeImagesAndClearAllowsSafeRebuilding() {
        var cache = new EmojiGlyphCache(2, 1 << 20);
        try (var surface = Surface.makeRasterN32Premul(200, 200); var paint = new Paint().setColor(0xFFFFFFFF)) {
            var font = FontManager.font(27.5F);
            for (String text : new String[]{"😀", "😃", "😄"}) assertTrue(cache.draw(surface.getCanvas(), font, text, 20, 60, paint));
            assertEquals(2, cache.size()); assertTrue(cache.bytes() > 0 && cache.bytes() <= (1 << 20));
            cache.clear(); assertEquals(0, cache.size()); assertEquals(0, cache.bytes());
            assertTrue(cache.draw(surface.getCanvas(), font, "😀", 20, 60, paint));
        } finally { cache.clear(); }
    }
    private static byte[] pixels(Surface surface, int width, int height) {
        try (var bitmap = new Bitmap()) { bitmap.allocN32Pixels(width, height); assertTrue(surface.readPixels(bitmap, 0, 0)); return bitmap.readPixels(); }
    }
}
