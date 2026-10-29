package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.text.RichText;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.humbleui.skija.BackendTexture;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.GLTextureInfo;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.SurfaceOrigin;
import io.github.humbleui.types.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.ItemStack;
import net.minecraft.text.HoverEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Native item renderer -> offscreen framebuffer -> borrowed Skija image.
 * Prepare in SkiaGraphics' pre-pass after the blur; draw only during Skia
 * paint. Render-thread only. First paint queues the icon and leaves its slot
 * blank.
 *
 * <p>1.21.5 draws items through {@code DrawContext} into an immediate vertex
 * consumer and resolves the destination from {@code RenderPhase.Target#get()}.
 * {@link RenderTargetOverride} points that at a per-icon framebuffer, so the
 * vanilla model renderer writes into a texture Skia can sample; hover events
 * expose the stack directly ({@code HoverEvent.ShowItem.item()}).</p>
 */
public final class ItemIconRenderer {
    private static final int PIXELS = 64;
    /** Same 0..16 ortho box the vanilla GUI item transform fills. */
    private static final Matrix4f PROJECTION = new Matrix4f().setOrtho(0.0F, 16.0F, 16.0F, 0.0F, -1000.0F, 1000.0F);
    private static final IconCache<IconKey, Image> CACHE = new IconCache<>(128, Image::close);
    private static final ConcurrentLinkedQueue<Framebuffer> RETIRED = new ConcurrentLinkedQueue<>();
    private static DirectContext owner;
    private static boolean failed;
    private static VertexConsumerProvider.Immediate immediate;

    private ItemIconRenderer() {
    }

    /**
     * Refreshes last frame's requested icons, including animations/custom models.
     * Textures are immutable generations: a borrowed image's framebuffer is never
     * reused or deleted until Skia invokes its release callback.
     */
    public static void prepare(DrawContext gui, SkiaGraphics bridge) {
        if (gui == null || bridge == null) return;
        if (!RenderSystem.isOnRenderThread()) return;
        if (failed) {
            CACHE.clear();
            releaseRetired();
            return;
        }
        List<IconKey> requests = CACHE.takeRequests();
        if (requests.isEmpty() && CACHE.isEmpty() && RETIRED.isEmpty()) return;
        try {
            DirectContext context = bridge.requireContext();
            if (owner != null && owner != context && !owner.isClosed()) {
                owner.resetGLAll();
                owner.flushAndSubmit(false);
            }
            // Submit all prior Skia sampling before closing/evicting images.
            context.resetGLAll();
            context.flushAndSubmit(false);
            if (owner != context || requests.isEmpty()) CACHE.clear();
            owner = context;
            releaseRetired();
            if (requests.isEmpty()) return;
            if (immediate == null) {
                // Private buffers: the screen's queued GUI geometry must not be
                // flushed into an icon framebuffer while the target override is on.
                immediate = VertexConsumerProvider.immediate(new BufferAllocator(262144));
            }
            // Independent pose/scissor bookkeeping; caller's GUI pose stays intact.
            var items = new DrawContext(MinecraftClient.getInstance(), immediate);
            Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            ProjectionType previousType = RenderSystem.getProjectionType();
            boolean scissor = RenderSystem.SCISSOR_STATE.isEnabled();
            int scissorX = RenderSystem.SCISSOR_STATE.getX();
            int scissorY = RenderSystem.SCISSOR_STATE.getY();
            int scissorWidth = RenderSystem.SCISSOR_STATE.getWidth();
            int scissorHeight = RenderSystem.SCISSOR_STATE.getHeight();
            RenderSystem.disableScissor();
            var modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            modelView.identity();
            try {
                RenderSystem.setProjectionMatrix(PROJECTION, ProjectionType.ORTHOGRAPHIC);
                for (IconKey key : requests) {
                    ItemStack stack = key.stack();
                    if (!stack.isEmpty()) CACHE.put(key, render(items, stack, context));
                }
            } finally {
                modelView.popMatrix();
                RenderSystem.setProjectionMatrix(previousProjection, previousType);
                if (scissor) RenderSystem.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                releaseRetired();
            }
        } catch (RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    /** No GL changes; current Canvas transforms, rounded clips and saveLayer alpha apply. */
    public static void draw(Canvas canvas, RichText.RichRun source, float x, float y, float size) {
        if (failed || canvas == null || source == null || source.style() == null
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(size) || size <= 0) return;
        HoverEvent hover = source.style().getHoverEvent();
        if (!(hover instanceof HoverEvent.ShowItem showItem)) return;
        ItemStack stack = showItem.item();
        if (stack == null || stack.isEmpty()) return;
        try {
            Image image = CACHE.request(new IconKey(stack.copy()));
            if (image != null) canvas.drawImageRect(image, Rect.makeXYWH(x, y, size, size));
        } catch (RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    private static Image render(DrawContext items, ItemStack stack, DirectContext context) {
        Framebuffer target = new SimpleFramebuffer("atomchat item icon", PIXELS, PIXELS, true);
        target.setFilter(FilterMode.LINEAR);
        boolean borrowed = false;
        try {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(target.getColorAttachment(), 0, target.getDepthAttachment(), 1.0);
            RenderTargetOverride.push(target);
            try {
                // Vanilla resolves model overrides and native custom renderers,
                // applies GUI lighting, fullbright and foil, and flushes itself.
                items.drawItem(stack, 0, 0);
            } finally {
                RenderTargetOverride.pop();
            }
            Image image;
            try (BackendTexture backend = BackendTexture.makeGL(PIXELS, PIXELS, false,
                    new GLTextureInfo(GL11C.GL_TEXTURE_2D, textureId(target.getColorAttachment()), 0x8058))) {
                image = Image.borrowTextureFrom(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                        ColorType.RGBA_8888, ColorAlphaType.PREMUL, null,
                        () -> RETIRED.add(target));
            }
            borrowed = true;
            return image;
        } finally {
            if (!borrowed) target.delete();
        }
    }

    /** Framebuffers die on the render thread: Skia's release callback may run anywhere. */
    private static void releaseRetired() {
        Framebuffer retired;
        while ((retired = RETIRED.poll()) != null) {
            retired.delete();
        }
    }

    private static int textureId(GpuTexture texture) {
        if (!(texture instanceof GlTexture glTexture)) {
            throw new IllegalStateException("AtomChat requires the OpenGL backend, got " + texture.getClass().getName());
        }
        return glTexture.getGlId();
    }

    private static void disable(Throwable error) {
        if (failed) return;
        failed = true;
        AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("render",
                "Native item icon bridge disabled after a rendering failure; inline slots remain blank"), error);
    }

    /**
     * Value key: the same item with the same components must hit the same
     * cached icon even when the hover event is rebuilt with a fresh stack
     * instance (record equality on ShowItem would compare stack identity).
     */
    private record IconKey(ItemStack stack) {
        @Override
        public boolean equals(Object other) {
            return other instanceof IconKey key
                    && stack.getItem() == key.stack.getItem()
                    && stack.getComponents().equals(key.stack.getComponents());
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(stack.getItem()) + stack.getComponents().hashCode();
        }
    }

    /** Pure request/LRU policy; tests require neither Minecraft bootstrap nor GL. */
    static final class IconCache<K, V> {
        private final int limit;
        private final Consumer<V> release;
        private final LinkedHashMap<K, V> images = new LinkedHashMap<>(16, 0.75F, true);
        private final LinkedHashSet<K> requested = new LinkedHashSet<>();

        IconCache(int limit, Consumer<V> release) {
            if (limit < 1) throw new IllegalArgumentException("limit must be positive");
            this.limit = limit;
            this.release = release;
        }

        V request(K key) {
            if (requested.size() < limit) requested.add(key);
            return images.get(key);
        }

        List<K> takeRequests() {
            List<K> result = new ArrayList<>(requested);
            requested.clear();
            return result;
        }

        void put(K key, V value) {
            V previous = images.put(key, value);
            if (previous != null && previous != value) release.accept(previous);
            if (images.size() > limit) {
                var eldest = images.entrySet().iterator();
                V removed = eldest.next().getValue();
                eldest.remove();
                release.accept(removed);
            }
        }

        boolean isEmpty() { return images.isEmpty(); }

        void clear() {
            requested.clear();
            images.values().forEach(release);
            images.clear();
        }
    }
}
