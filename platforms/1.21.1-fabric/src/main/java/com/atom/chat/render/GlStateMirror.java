package com.atom.chat.render;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * Platform seam over the parts of Blaze3D's GL state cache that shared code
 * touches. 1.21.5 moved {@code GlStateManager} to another package and dropped
 * the blend-equation and stencil caches, so shared code cannot name it directly.
 */
public final class GlStateMirror {
    private GlStateMirror() {
    }

    public static int textureCount() {
        return GlStateManager.TEXTURE_COUNT;
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
        GlStateManager._blendEquation(mode);
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
        GlStateManager._stencilFunc(func, ref, mask);
    }

    public static void stencilOp(int sfail, int dpfail, int dppass) {
        GlStateManager._stencilOp(sfail, dpfail, dppass);
    }

    public static void stencilMask(int mask) {
        GlStateManager._stencilMask(mask);
    }

    /** 1.21.8 drops the command encoder's cached program/pipeline after raw GL. */
    public static void afterRawGl() {
    }
}
