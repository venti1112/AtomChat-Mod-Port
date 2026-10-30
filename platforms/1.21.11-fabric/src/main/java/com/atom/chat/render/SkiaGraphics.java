package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.diagnostics.FrameProfile;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;

/**
 * 1.21.8 Skia bridge: paints into an offscreen GPU texture and hands the
 * finished image to the batched GUI pipeline as one textured quad.
 *
 * <p>1.21.5 stopped letting screens draw straight onto the main framebuffer —
 * draw calls are recorded into {@code GuiRenderState} and executed later — so
 * the base platform's "wrap the main FBO" approach no longer works. The main
 * framebuffer is still sampled (before the GUI runs) for the panel blur.</p>
 */
public class SkiaGraphics {
    private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_DST
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private static int leakLogCount;
    private Canvas canvas;
    private DirectContext context;
    private Surface surface;
    private BackendRenderTarget renderTarget;
    private GpuTexture uiTexture;
    private GpuTextureView uiView;
    private int uiFbo;
    private int width;
    private int height;
    private int lastFramebufferWidth = -1;
    private int lastFramebufferHeight = -1;
    private boolean surfaceDirty;

    public void checkFrameBufferId() {
        var fb = MinecraftClient.getInstance().getFramebuffer();
        int fbWidth = fb.textureWidth;
        int fbHeight = fb.textureHeight;
        if (lastFramebufferWidth != -1
                && (lastFramebufferWidth != fbWidth || lastFramebufferHeight != fbHeight)) {
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
        lastFramebufferWidth = width;
        lastFramebufferHeight = height;

        uiTexture = RenderSystem.getDevice().createTexture("atomchat ui", TEXTURE_USAGE,
                TextureFormat.RGBA8, width, height, 1, 1);
        uiView = RenderSystem.getDevice().createTextureView(uiTexture);

        int previousFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        uiFbo = GL30C.glGenFramebuffers();
        try {
            GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, uiFbo);
            GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D, textureId(uiTexture), 0);
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
        if (uiView != null) {
            uiView.close();
            uiView = null;
        }
        if (uiTexture != null) {
            uiTexture.close();
            uiTexture = null;
        }
        if (uiFbo != 0) {
            GL30C.glDeleteFramebuffers(uiFbo);
            uiFbo = 0;
        }
        canvas = null;
    }

    /**
     * Draws the Skia UI after an optional pre-pass (native item icons) and
     * composites the result into the GUI render state.
     *
     * @param density {@code <= 0} falls back to the 1080p-anchored default.
     */
    public void draw(DrawContext gui, Runnable preUi, float density,
                     java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        long profileStart = FrameProfile.start();
        long allocatedBefore = profileStart == 0L ? -1L : FrameProfile.allocatedBytes();
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
            if (preUi != null) {
                preUi.run();
            }
            prepareSkiaGl();
            Image world = captureWorld();
            canvas.clear(0x00000000);

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

            gui.state.addSimpleElement(new TexturedQuadGuiElementRenderState(
                    RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    TextureSetup.of(uiView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                    new Matrix3x2f(),
                    0, 0, gui.getScaledWindowWidth(), gui.getScaledWindowHeight(),
                    0.0F, 1.0F, 1.0F, 0.0F,
                    -1,
                    null));
        } finally {
            GlStateUtil.restore();
        }
        if (FrameProfile.frame(profileStart, allocatedBefore)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("render", FrameProfile.summary()));
        }
    }

    /**
     * Skia caches GL bindings internally; vanilla passes and our native item
     * renderer move them behind its back. Sampler objects bound by 1.21.8's
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

    private Image captureWorld() {
        try {
            Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
            int texture = textureId(fb.getColorAttachment());
            try (BackendTexture backend = BackendTexture.makeGL(fb.textureWidth, fb.textureHeight, false,
                    new GLTextureInfo(GL11C.GL_TEXTURE_2D, texture, 0x8058))) {
                return Image.borrowTextureFrom(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                        ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.getDisplayP3(), () -> {
                        });
            }
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

    public Surface requireSurface() {
        if (surface == null) {
            throw new IllegalStateException("Surface not created");
        }
        return surface;
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
