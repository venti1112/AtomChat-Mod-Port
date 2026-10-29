package com.atom.chat.render;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.SamplingMode;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;

/**
 * 1.21.8 panel blur: a request recorded before the UI is painted, then applied
 * inside the same Skia canvas from the captured main framebuffer.
 *
 * <p>The base platform blurred with a raw-GL Kawase chain and drew the result
 * onto the main framebuffer before Skia ran. On 1.21.8 the UI is composited
 * later as a single texture, so the blur has to be part of that texture; doing
 * it in Skia also keeps the shader/pipeline rules of the new renderer out of
 * the blur path.</p>
 */
public final class PanelBlurRenderer {
    private static final float SIGMA = 18.0F;
    private static Request pending;
    private static boolean landed;
    private static boolean disabled;

    private PanelBlurRenderer() {
    }

    /** Kept for the base call site; the Skia path has nothing to load. */
    public static void ensureLoaded() {
    }

    public static boolean isAvailable() {
        return !disabled;
    }

    /**
     * Records the blurred region for this frame's Skia paint.
     *
     * @param x      panel left in panel-space units (the Skia canvas is scaled by density)
     * @param y      panel top in panel-space units
     * @param width  panel width in panel-space units
     * @param height panel height in panel-space units
     * @param radius rounded-corner radius in panel-space units
     * @param alpha  overall fade alpha (0..1, follows the panel open animation)
     * @return true when the request was accepted; whether it actually painted is
     *         reported by {@link #landedThisFrame()} after the frame's paint
     */
    public static boolean render(float x, float y, float width, float height, float radius, float alpha) {
        landed = false;
        pending = null;
        if (disabled || alpha <= 0.003F || width <= 0.0F || height <= 0.0F) {
            return false;
        }
        pending = new Request(x, y, width, height, radius, Math.min(1.0F, alpha));
        return true;
    }

    /** Paints and clears the pending request; runs with the density scale applied. */
    static void paint(Canvas canvas, Image world, float density) {
        Request request = pending;
        pending = null;
        landed = false;
        if (request == null) {
            return;
        }
        try (Paint paint = new Paint();
             ImageFilter blur = ImageFilter.makeBlur(SIGMA * density, SIGMA * density, FilterTileMode.CLAMP)) {
            paint.setAntiAlias(true);
            paint.setImageFilter(blur);
            paint.setAlphaf(request.alpha);
            canvas.save();
            try {
                canvas.clipRRect(RRect.makeXYWH(request.x, request.y, request.width, request.height,
                        request.radius), true);
                canvas.drawImageRect(world,
                        Rect.makeXYWH(request.x * density, request.y * density,
                                request.width * density, request.height * density),
                        Rect.makeXYWH(request.x, request.y, request.width, request.height),
                        SamplingMode.LINEAR, paint, true);
            } finally {
                canvas.restore();
            }
            landed = true;
        } catch (RuntimeException | LinkageError error) {
            disable(error.getClass().getSimpleName());
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                    "AtomChat panel blur failed inside Skia; the panel keeps its solid background"), error);
        }
    }

    /** Whether this frame's request really reached the canvas (drawPanel's tint gate). */
    public static boolean landedThisFrame() {
        return landed;
    }

    /**
     * Trips the breaker for the rest of the session: a deterministic Skia
     * failure would otherwise repeat every frame and bury the first cause.
     */
    public static void disable(String reason) {
        if (disabled) {
            return;
        }
        disabled = true;
        pending = null;
        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                "AtomChat panel blur disabled for this session after the first failure "
                        + "({}); the panel keeps its solid background"), reason);
    }

    /** Resource reload hook; the Skia path has no shader to reset. */
    public static void resetShader() {
    }

    private record Request(float x, float y, float width, float height, float radius, float alpha) {
    }
}
