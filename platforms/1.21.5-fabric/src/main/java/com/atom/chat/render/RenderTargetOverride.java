package com.atom.chat.render;

import net.minecraft.client.gl.Framebuffer;

/**
 * Render-thread-only redirect for {@code RenderPhase.Target#get()}.
 *
 * <p>1.21.5 resolves every layer draw's framebuffer through that one method, so
 * pointing it at an offscreen framebuffer is the only way to render vanilla
 * item models somewhere other than the main target. The value never leaves the
 * render thread and is always cleared in a {@code finally}.</p>
 */
public final class RenderTargetOverride {
    private static Framebuffer override;

    private RenderTargetOverride() {
    }

    public static void push(Framebuffer framebuffer) {
        override = framebuffer;
    }

    public static void pop() {
        override = null;
    }

    public static Framebuffer current() {
        return override;
    }
}
