package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.diagnostics.FrameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.PixelGeometry;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.skija.SurfaceProps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL33C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Skia rendering bridge that draws directly onto Minecraft's main framebuffer.
 * Pattern ported from Tuui's Graphics class.
 */
public class SkiaGraphics {
    private static int leakLogCount;
    private static ShaderProgram compositeShader;
    private static boolean compositeLoadAttempted;
    private Canvas canvas;
    private DirectContext context;
    private Surface surface;
    private BackendRenderTarget renderTarget;
    private int lastFrameBufferId = -1;
    private int lastFramebufferWidth = -1;
    private int lastFramebufferHeight = -1;
    private boolean surfaceDirty;

    /**
     * How the panel reaches the screen.
     *
     * <p>{@code GL} is the desktop path: Skia wraps Minecraft's framebuffer as a
     * {@link BackendRenderTarget} and draws into it through {@link DirectContext#makeGL()}.
     *
     * <p>{@code RASTER} exists because on Android/FCL that call returns nullptr — Skija's GL
     * binding looks up the current EGL context through the <em>system</em> {@code libEGL.so},
     * while Minecraft's context belongs to MobileGlues. Nothing can bridge the two, so the
     * panel is drawn by Skia's CPU rasterizer into an RGBA buffer and then composited with a
     * plain textured quad.
     */
    private enum Backend { GL, RASTER }

    private Backend backend;
    private ByteBuffer rasterPixels;
    private Pixmap rasterPixmap;
    private int rasterWidth = -1;
    private int rasterHeight = -1;
    private int compositeTexture = -1;
    private int compositeTextureWidth = -1;
    private int compositeTextureHeight = -1;

    public void checkFrameBufferId() {
        var fb = MinecraftClient.getInstance().getFramebuffer();
        int current = fb.fbo;
        int width = fb.textureWidth;
        int height = fb.textureHeight;
        // Minecraft resizes the main framebuffer in place when the window
        // changes (F11 fullscreen toggle, dragging a windowed border), keeping
        // the same FBO id. The Skia surface must be recreated on size changes
        // too, otherwise its coordinate system goes stale while the raw-GL blur
        // pre-pass keeps using the new size — that is the small-window
        // misalignment/blur-offset bug.
        if (lastFrameBufferId != -1
                && (lastFrameBufferId != current
                || lastFramebufferWidth != width
                || lastFramebufferHeight != height)) {
            surfaceDirty = true;
        }
    }

    public void createSurface() {
        var fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth;
        int height = fb.textureHeight;
        int fbo = fb.fbo;
        lastFramebufferWidth = width;
        lastFramebufferHeight = height;

        if (backend == null) {
            try {
                context = DirectContext.makeGL();
                backend = Backend.GL;
            } catch (RuntimeException failure) {
                // Android/FCL: Skija's GL binding asks the system libEGL for the current
                // context, but Minecraft's context lives in MobileGlues. Deciding here and
                // only here keeps a broken makeGL from throwing out of every frame.
                backend = Backend.RASTER;
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                        "Skia GPU context unavailable ({}); drawing the panel with the CPU rasterizer and compositing it through a texture"),
                        failure.getMessage());
            }
        }
        lastFrameBufferId = fbo;
        surfaceDirty = false;

        if (backend == Backend.RASTER) {
            createRasterSurface(width, height);
            return;
        }

        if (surface != null) {
            surface.close();
        }
        if (renderTarget != null) {
            renderTarget.close();
        }

        renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbo, 32856);
        surface = Surface.wrapBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                ColorType.RGBA_8888,
                ColorSpace.getDisplayP3(),
                new SurfaceProps(PixelGeometry.RGB_H)
        );
        canvas = surface != null ? surface.getCanvas() : null;
    }

    /**
     * CPU-backed surface at framebuffer resolution, drawn into the pixel buffer the composite
     * quad uploads. A direct buffer is required: {@code Pixmap.make} hands Skia the raw address,
     * and a heap array would be copied behind our back.
     */
    private void createRasterSurface(int width, int height) {
        // Surface first: it draws into the pixmap's pixels, so the pixmap must outlive it.
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (rasterPixmap != null) {
            rasterPixmap.close();
            rasterPixmap = null;
        }
        rasterWidth = width;
        rasterHeight = height;
        rasterPixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        rasterPixmap = Pixmap.make(new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
                rasterPixels, width * 4);
        surface = Surface.makeRasterDirect(rasterPixmap);
        canvas = surface != null ? surface.getCanvas() : null;
    }

    /**
     * Uploads the raster pixels and draws them across the whole GUI.
     *
     * <p>The quad is expressed in GUI units (the projection Minecraft has bound while a screen
     * renders) and the texture is framebuffer sized, so the two line up 1:1. Skia's raster
     * output is premultiplied, hence {@code ONE / 1-srcalpha} instead of the default
     * straight-alpha blend — the latter would darken every antialiased edge.
     */
    private void compositeRaster() {
        ShaderProgram shader = compositeShader();
        if (shader == null || rasterPixels == null || rasterWidth <= 0 || rasterHeight <= 0) {
            return;
        }
        ensureCompositeTexture(rasterWidth, rasterHeight);

        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._bindTexture(compositeTexture);
        GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, rasterWidth, rasterHeight,
                GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, rasterPixels);

        var window = MinecraftClient.getInstance().getWindow();
        float guiWidth = window.getScaledWidth();
        float guiHeight = window.getScaledHeight();
        Matrix4f pose = new Matrix4f();

        RenderSystem.setShaderTexture(0, compositeTexture);
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // GL textures are bottom-up; Skia's first row is the top, so v=0 belongs at y=0.
        builder.vertex(pose, 0.0F, 0.0F, 0.0F).texture(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F);
        builder.vertex(pose, 0.0F, guiHeight, 0.0F).texture(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F);
        builder.vertex(pose, guiWidth, guiHeight, 0.0F).texture(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, 1.0F);
        builder.vertex(pose, guiWidth, 0.0F, 0.0F).texture(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, 1.0F);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private void ensureCompositeTexture(int width, int height) {
        if (compositeTexture != -1 && compositeTextureWidth == width && compositeTextureHeight == height) {
            return;
        }
        if (compositeTexture == -1) {
            compositeTexture = GlStateManager._genTexture();
        }
        compositeTextureWidth = width;
        compositeTextureHeight = height;
        GlStateManager._bindTexture(compositeTexture);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_LINEAR);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, width, height, 0,
                GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    private static ShaderProgram compositeShader() {
        if (!compositeLoadAttempted) {
            compositeLoadAttempted = true;
            try {
                compositeShader = new ShaderProgram(
                        MinecraftClient.getInstance().getResourceManager(),
                        "atomchat_ui_composite",
                        VertexFormats.POSITION_TEXTURE_COLOR);
            } catch (Throwable t) {
                AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                        "AtomChat raster composite shader failed to load; the panel stays blank"), t);
                compositeShader = null;
            }
        }
        return compositeShader;
    }

    public void draw(java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(null, renderer);
    }

    public void draw(Runnable preUi, java.util.function.BiConsumer<Canvas, Image> renderer) {
        draw(preUi, 0.0F, renderer);
    }

    /**
     * Draws the Skia UI after an optional raw-GL pre-pass, with an explicit
     * design density. AtomChat passes {@code baseDensity * uiScale} so its own
     * scale option scales the whole panel uniformly — UiTokens constants stay
     * untouched. The pre-pass runs after {@link GlStateUtil#save()} and before
     * Skia paints anything; {@link GlStateUtil#restore()} cleans up afterwards.
     *
     * @param density {@code <= 0} falls back to the 1080p-anchored default.
     */
    public void draw(Runnable preUi, float density, java.util.function.BiConsumer<Canvas, Image> renderer) {
        RenderSystem.assertOnRenderThread();
        // 尺子：整帧计时 + 这一帧在渲染线程上新增的对象分配。开关在 FrameProfile 自己手里，
        // 关掉时 start() 返回 0，下面两个调用点什么都不做。
        long profileStart = FrameProfile.start();
        long allocatedBefore = profileStart == 0L ? -1L : FrameProfile.allocatedBytes();
        GlStateUtil.save();
        try {
            if (surface == null || canvas == null || surfaceDirty) {
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
            // Capture the world before Skia resets its resource tracking: resetAll()
            // can drop the bookkeeping for our adopted texture-backed image.
            Image snapshot = snapshotWorld();
            if (backend == Backend.GL) {
                context.resetAll();
            } else {
                // The raster canvas is reused across frames; without an explicit clear the
                // previous frame stays in the buffer and the panel could never fade out.
                canvas.clear(0x00000000);
            }
            RenderSystem.enableBlend();

            // Guard rail: remember the stack baseline. A renderer that forgets a
            // matching restore (typically saveLayer) used to leak a matrix per
            // frame, and because the density scale is applied every frame the leak
            // compounded into an exploding transform that flung the whole UI off
            // screen — the 0.2.4 notification banner rollback. Rewinding to the
            // baseline caps any future leak at a single frame.
            int saveBase = canvas.getSaveCount();
            canvas.save();
            // Decouple from vanilla GUI scale: design density anchored at 1080p.
            canvas.scale(density, density);
            try {
                renderer.accept(canvas, snapshot);
            } finally {
                int leaked = canvas.getSaveCount() - (saveBase + 1);
                if (leaked != 0 && com.atom.chat.config.AtomChatConfig.get().debug) {
                    leakLogCount++;
                    // Throttled: a per-frame leak used to spam the log line-by-line.
                    if (leakLogCount == 1 || leakLogCount % 200 == 0) {
                        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render", "Skia canvas stack leak: {} unbalanced save/saveLayer (occurrence {})"),
                                leaked, leakLogCount);
                    }
                }
                canvas.restoreToCount(saveBase);
            }

            if (backend == Backend.GL) {
                context.flush(surface);
            } else {
                compositeRaster();
            }
        } finally {
            GlStateUtil.restore();
        }
        // 到报告点就打一份分布（六十帧一次，约十秒）。这个类自己不打日志：格式留在 FrameProfile
        // 里，于是它的每一行都能在单测里拼出来 —— 一个要装全局渲染状态才能测的诊断类没人测。
        if (FrameProfile.frame(profileStart, allocatedBefore)) {
            AtomChat.LOGGER.info(com.atom.chat.diagnostics.AtomChatLog.message("render", FrameProfile.summary()));
        }
    }

    /**
     * Legacy Skia-side snapshot path. Intentionally returns null: the panel
     * blur is now implemented by {@link PanelBlurRenderer} with raw GL and a
     * core shader outside Skia, so no Skia GPU resource crosses
     * {@code context.resetAll()}. Do not re-introduce Skia snapshots here.
     */
    private Image snapshotWorld() {
        return null;
    }

    /** No-op; kept so callers need no change. */
    public void releaseWorldSnapshot() {
    }

    private void glStorePixel() {
        GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
    }

    /** False while the bridge draws through the CPU rasterizer (no Skia GPU context). */
    public boolean hasGpuContext() {
        return context != null;
    }

    public DirectContext requireContext() {
        if (context == null) {
            throw new IllegalStateException("Skia context not initialized");
        }
        return context;
    }

    public BackendRenderTarget requireRenderTarget() {
        if (renderTarget == null) {
            throw new IllegalStateException("Render target not created");
        }
        return renderTarget;
    }

    public Surface requireSurface() {
        if (surface == null) {
            throw new IllegalStateException("Surface not created");
        }
        return surface;
    }

    /** Screen-owned bridges release GPU resources when their screen is removed. */
    public void close() {
        if (surface != null) { surface.close(); surface = null; }
        if (rasterPixmap != null) { rasterPixmap.close(); rasterPixmap = null; }
        rasterPixels = null;
        if (compositeTexture != -1) {
            GlStateManager._deleteTexture(compositeTexture);
            compositeTexture = -1;
            compositeTextureWidth = -1;
            compositeTextureHeight = -1;
        }
        if (renderTarget != null) { renderTarget.close(); renderTarget = null; }
        if (context != null) { context.close(); context = null; }
        canvas = null;
    }
}
