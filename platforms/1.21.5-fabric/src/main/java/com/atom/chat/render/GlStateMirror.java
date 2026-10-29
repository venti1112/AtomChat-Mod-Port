package com.atom.chat.render;

import com.atom.chat.mixin.GlResourceManagerAccessor;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 1.21.5 seam: {@code GlStateManager} lives in {@code blaze3d.opengl} and the
 * blend-equation and stencil caches are gone. The backend still caches the
 * bound program on its command encoder and skips {@code glUseProgram} when the
 * next pass wants the same one, so raw GL work has to drop that cache.
 */
public final class GlStateMirror {
    /** Blaze3D 1.21.8 still caches twelve 2D texture units. */
    private static final int TEXTURE_COUNT = 12;

    private GlStateMirror() {
    }

    public static int textureCount() {
        return TEXTURE_COUNT;
    }

    public static void enableBlend() {
        GlStateManager._enableBlend();
    }

    public static void disableBlend() {
        GlStateManager._disableBlend();
    }

    public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void blendEquation(int mode) {
        // No blend-equation cache in 1.21.5+; the raw GL write is the whole restore.
    }

    public static void enableDepthTest() {
        GlStateManager._enableDepthTest();
    }

    public static void disableDepthTest() {
        GlStateManager._disableDepthTest();
    }

    public static void depthMask(boolean mask) {
        GlStateManager._depthMask(mask);
    }

    public static void depthFunc(int func) {
        GlStateManager._depthFunc(func);
    }

    public static void enableCull() {
        GlStateManager._enableCull();
    }

    public static void disableCull() {
        GlStateManager._disableCull();
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GlStateManager._colorMask(red, green, blue, alpha);
    }

    public static void activeTexture(int texture) {
        GlStateManager._activeTexture(texture);
    }

    public static void bindTexture(int texture) {
        GlStateManager._bindTexture(texture);
    }

    public static void enableScissorTest() {
        GlStateManager._enableScissorTest();
    }

    public static void disableScissorTest() {
        GlStateManager._disableScissorTest();
    }

    public static void bindDrawFramebuffer(int framebuffer) {
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER, framebuffer);
    }

    public static void bindReadFramebuffer(int framebuffer) {
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, framebuffer);
    }

    public static void viewport(int x, int y, int width, int height) {
        GlStateManager._viewport(x, y, width, height);
    }

    public static void stencilFunc(int func, int ref, int mask) {
        // No stencil cache in 1.21.5+; the raw GL write is the whole restore.
    }

    public static void stencilOp(int sfail, int dpfail, int dppass) {
    }

    public static void stencilMask(int mask) {
    }

    public static void afterRawGl() {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        if (encoder instanceof GlResourceManagerAccessor accessor) {
            // createRenderPass already resets currentPipeline per pass; the
            // program is the one that survives across passes and would make the
            // next pass skip its glUseProgram while Skia's program is bound.
            accessor.atomchat$setCurrentProgram(null);
            accessor.atomchat$setCurrentPipeline(null);
        }
    }
}
