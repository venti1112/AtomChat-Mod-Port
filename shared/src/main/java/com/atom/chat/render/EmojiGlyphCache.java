package com.atom.chat.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.Rect;
import java.util.LinkedHashMap;

/**
 * Rasterize colour-font runs at a stable resolution, then let Skia transform an image.
 * Scaling colour-font text directly regenerates expensive colour glyphs during each animation frame.
 * CPU-backed images survive GL context replacement. Render-thread only; eviction closes native images.
 */
final class EmojiGlyphCache {
    private record Key(Font font, String text, int rgb) {
        @Override public int hashCode() { return (System.identityHashCode(font) * 31 + text.hashCode()) * 31 + rgb; }
        @Override public boolean equals(Object other) {
            return other instanceof Key k && font == k.font && rgb == k.rgb && text.equals(k.text);
        }
    }
    private record Glyph(Image image, Rect logicalBounds, int scale, long bytes) { }
    private final int maxEntries;
    private final long maxBytes;
    private final LinkedHashMap<Key, Glyph> entries = new LinkedHashMap<>(64, .75F, true);
    private long bytes, builds;
    EmojiGlyphCache() { this(256, 32L << 20); }
    EmojiGlyphCache(int maxEntries, long maxBytes) {
        if (maxEntries <= 0 || maxBytes <= 0) throw new IllegalArgumentException("positive glyph cache limits required");
        this.maxEntries = maxEntries; this.maxBytes = maxBytes;
    }
    boolean draw(Canvas canvas, Font font, String text, float x, float y, Paint paint) {
        if (text.isEmpty() || !SkiaFontRenderer.isEmojiCodepoint(text.codePointAt(0))) return false;
        var key = new Key(font, text, paint.getColor() & 0xFFFFFF);
        Glyph glyph = entries.get(key);
        int scale = resolution(canvas);
        if (glyph == null || glyph.scale() < scale) {
            Glyph replacement = rasterize(font, text, key.rgb(), scale);
            if (replacement != null) {
                if (glyph != null) { bytes -= glyph.bytes(); glyph.image().close(); }
                glyph = replacement; entries.put(key, glyph); bytes += glyph.bytes();
                trim();
            }
        }
        if (glyph == null) return false;
        var bounds = glyph.logicalBounds();
        canvas.drawImageRect(glyph.image(), Rect.makeXYWH(0, 0, glyph.image().getWidth(), glyph.image().getHeight()),
                Rect.makeXYWH(x + bounds.getLeft(), y + bounds.getTop(), bounds.getWidth(), bounds.getHeight()),
                SamplingMode.LINEAR, paint, false);
        return true;
    }
    private static int resolution(Canvas canvas) {
        float[] m = canvas.getLocalToDeviceAsMatrix33().getMat();
        double sum = m[0] * m[0] + m[1] * m[1] + m[3] * m[3] + m[4] * m[4];
        double determinant = m[0] * m[4] - m[1] * m[3];
        double density = Math.sqrt((sum + Math.sqrt(Math.max(0, sum * sum - 4 * determinant * determinant))) / 2);
        int scale = 2;
        while (scale < 8 && scale < density) scale *= 2;
        return scale;
    }
    private Glyph rasterize(Font font, String text, int rgb, int scale) {
        Rect ink = font.measureText(text);
        if (ink.isEmpty()) return null;
        // One backing pixel of antialias coverage, not UI padding or a layout margin.
        float left = ((float)Math.floor(ink.getLeft() * scale) - 1) / scale;
        float top = ((float)Math.floor(ink.getTop() * scale) - 1) / scale;
        int width = (int)Math.ceil(ink.getRight() * scale) + 1 - Math.round(left * scale);
        int height = (int)Math.ceil(ink.getBottom() * scale) + 1 - Math.round(top * scale);
        long cost = (long)width * height * 4;
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || cost > maxBytes) return null;
        try (Surface raster = Surface.makeRasterN32Premul(width, height); Paint opaque = new Paint().setColor(0xFF000000 | rgb)) {
            var target = raster.getCanvas(); target.clear(0); target.scale(scale, scale); target.translate(-left, -top);
            target.drawString(text, 0, 0, font, opaque);
            Image image = raster.makeImageSnapshot(); builds++;
            return new Glyph(image, Rect.makeXYWH(left, top, (float)width / scale, (float)height / scale), scale, cost);
        }
    }
    private void trim() {
        var iterator = entries.entrySet().iterator();
        while ((entries.size() > maxEntries || bytes > maxBytes) && iterator.hasNext()) {
            Glyph glyph = iterator.next().getValue(); iterator.remove(); bytes -= glyph.bytes(); glyph.image().close();
        }
    }
    void clear() { for (Glyph glyph : entries.values()) glyph.image().close(); entries.clear(); bytes = 0; builds = 0; }
    int size() { return entries.size(); }
    long bytes() { return bytes; }
    long builds() { return builds; }
}
