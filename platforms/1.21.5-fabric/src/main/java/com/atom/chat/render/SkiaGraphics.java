package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.diagnostics.FrameProfile;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;

/**
 * 1.21.5 Skia bridge: paints straight into Minecraft's main framebuffer.
 *
 * <p>1.21.5 moved drawing to {@code GpuDevice}/{@code RenderPass} but has no
 * GUI render-state batching yet, so — unlike 1.21.6+ — the screen can still
 * touch the main target between flush points. The bridge therefore flushes the
 * screen's queued geometry first, then paints Skia into an FBO whose only
 * attachment is the main framebuffer's colour texture. The panel blur samples
 * a one-off copy of that texture: sampling the render target itself is a
 * feedback loop Skia refuses to resolve.</p>
 */
public class SkiaGraphics {
    private static int leakLogCount;
    private Canvas canvas;
    private DirectContext context;
    private Surface surface;
    private BackendRenderTarget renderTarget;
    private GpuTexture worldCopy;
    private int width;
    private int height;
    private int uiFbo;
    private int lastColorTextureId = -1;
    private int lastFramebufferWidth = -1;
    private int lastFramebufferHeight = -1;
    private boolean surfaceDirty;

    public void checkFrameBufferId() {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int colorTexture = ((GlTexture) fb.getColorAttachment()).getGlId();
        // A resize replaces both attachments in place, so the main framebuffer
        // keeps its identity while the colour texture gets a new GL name.
        if (lastColorTextureId != -1
                && (lastColorTextureId != colorTexture
                || lastFramebufferWidth != fb.textureWidth
                || lastFramebufferHeight != fb.textureHeight)) {
            surfaceDirty = true;
        }
    }

    public void createSurface() {
        if (context == null) {
            context = DirectContext.makeGL();
        }
        destroySurfaceResources();

        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        width = fb.textureWidth;
        height = fb.textureHeight;
        int colorTexture = ((GlTexture) fb.getColorAttachment()).getGlId();
        lastColorTextureId = colorTexture;
        lastFramebufferWidth = width;
        lastFramebufferHeight = height;

        int previousFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        uiFbo = GL30C.glGenFramebuffers();
        try {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, uiFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, colorTexture, 0);
            int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
            if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete Skia framebuffer: 0x" + Integer.toHexString(status));
            }
        } finally {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFbo);
        }

        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 0, uiFbo, 0x8058);
        surface = Surface.wrapBackendRenderTarget(context, renderTarget,
                SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, ColorSpace.getDisplayP3(),
                new SurfaceProps(PixelGeometry.RGB_H));
        canvas = surface.getCanvas();
        surfaceDirty = false;
    }

    private void destroySurfaceResources() {
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (worldCopy != null) {
            worldCopy.close();
            worldCopy = null;
        }
        if (uiFbo != 0) {
            GL30C.glDeleteFramebuffers(uiFbo);
            uiFbo = 0;
        }
        canvas = null;
    }

    /**
     * Flushes the screen's queued geometry, then paints the Skia UI after an
     * optional pre-pass (native item icons).
     *
     * @param density {@code <= 0} falls back to the 1080p-anchored default.
     */
    public void draw(DrawContext gui, Runnable preUi, float density,
                     java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        long profileStart = FrameProfile.start();
        long allocatedBefore = profileStart == 0L ? -1L : FrameProfile.allocatedBytes();
        // 1.21.6+ records GUI draws into a render state; 1.21.5 still flushes
        // vertex buffers here, so the Skia paint must follow a real flush.
        if (gui != null) {
            gui.draw();
        }
        GlStateUtil.save();
        try {
            if (context == null || surface == null || canvas == null || surfaceDirty) {
                createSurface();
            }
            if (canvas == null) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "Skia canvas is null after createSurface, skipping frame"));
                return;
            }
            if (density <= 0.0F) {
                density = Math.max(1.0F, MinecraftClient.getInstance().getFramebuffer().textureHeight / 1080.0F);
            }
            glStorePixel();
            if (preUi != null) {
                preUi.run();
            }
            // The copy has to run before Skia's GL cache is reset and before
            // Skia issues any GL call of its own.
            boolean worldCopied = PanelBlurRenderer.hasPending() && copyWorld();
            prepareSkiaGl();
            Image world = worldCopied ? borrowWorld() : null;

            try {
                int saveBase = canvas.getSaveCount();
                canvas.save();
                canvas.scale(density, density);
                try {
                    if (world != null) {
                        PanelBlurRenderer.paint(canvas, world, density);
                    }
                    renderer.accept(canvas, null);
                } finally {
                    int leaked = canvas.getSaveCount() - (saveBase + 1);
                    if (leaked != 0 && com.atom.chat.config.AtomChatConfig.get().debug) {
                        leakLogCount++;
                        if (leakLogCount == 1 || leakLogCount % 200 == 0) {
                            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "Skia canvas stack leak: {} unbalanced save/saveLayer (occurrence {})"),
                                    leaked, leakLogCount);
                        }
                    }
                    canvas.restoreToCount(saveBase);
                }
                context.flush(surface);
            } finally {
                if (world != null) {
                    world.close();
                }
            }
        } finally {
            GlStateUtil.restore();
        }
        if (FrameProfile.frame(profileStart, allocatedBefore)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("render", FrameProfile.summary()));
        }
    }

    private void glStorePixel() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
    }

    /**
     * Skia caches GL bindings internally; vanilla passes and our native item
     * renderer move them behind its back. Sampler objects bound by 1.21.5's
     * pipelines would also override Skia's glyph-atlas texture parameters.
     */
    private void prepareSkiaGl() {
        context.resetGLAll();
        int units = GL11C.glGetInteger(GL20C.GL_MAX_TEXTURE_IMAGE_UNITS);
        for (int unit = 0; unit < units; unit++) {
            GL33C.glBindSampler(unit, 0);
        }
        GL30C.glBindBuffer(GL30C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
    }

    /** Copies the main colour texture so the blur never samples its own target. */
    private boolean copyWorld() {
        try {
            Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
            if (worldCopy == null
                    || worldCopy.getWidth(0) != fb.textureWidth
                    || worldCopy.getHeight(0) != fb.textureHeight) {
                if (worldCopy != null) {
                    worldCopy.close();
                }
                worldCopy = RenderSystem.getDevice().createTexture("atomchat world snapshot",
                        TextureFormat.RGBA8, fb.textureWidth, fb.textureHeight, 1);
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    fb.getColorAttachment(), worldCopy, 0, 0, 0, 0, 0, fb.textureWidth, fb.textureHeight);
            return true;
        } catch (RuntimeException | LinkageError error) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                    "World snapshot unavailable; panel blur falls back to the solid background"), error);
            return false;
        }
    }

    private Image borrowWorld() {
        try (BackendTexture backend = BackendTexture.makeGL(width, height, false,
                new GLTextureInfo(GL11C.GL_TEXTURE_2D, textureId(worldCopy), 0x8058))) {
            return Image.borrowTextureFrom(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                    ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.getDisplayP3(), () -> {
                    });
        } catch (RuntimeException | LinkageError error) {
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                    "World snapshot unavailable; panel blur falls back to the solid background"), error);
            return null;
        }
    }

    private static int textureId(GpuTexture texture) {
        if (!(texture instanceof GlTexture glTexture)) {
            throw new IllegalStateException("AtomChat requires the OpenGL backend, got " + texture.getClass().getName());
        }
        return glTexture.getGlId();
    }

    public DirectContext requireContext() {
        if (context == null) {
            throw new IllegalStateException("Skia context not initialized");
        }
        return context;
    }

    /** The borrowed world image is per-frame; nothing survives the frame. */
    public void releaseWorldSnapshot() {
    }

    public void close() {
        destroySurfaceResources();
        if (context != null) {
            context.close();
            context = null;
        }
    }
}
