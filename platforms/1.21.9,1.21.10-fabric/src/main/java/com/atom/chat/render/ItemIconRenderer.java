package com.atom.chat.render;

import com.atom.chat.AtomChat;
import com.atom.chat.text.RichText;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.HoverEvent;
import org.lwjgl.opengl.GL11C;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Native item renderer -> offscreen texture -> borrowed Skija image.
 * Prepare in SkiaGraphics' pre-pass after the blur; draw only during Skia
 * paint. Render-thread only. First paint queues the icon and leaves its slot
 * blank.
 *
 * <p>1.21.8 renders item models through {@code KeyedItemRenderState} into a
 * device texture instead of old immediate-mode GL, and hover events expose the
 * stack directly ({@code HoverEvent.ShowItem.item()}).</p>
 */
public final class ItemIconRenderer {
    private static final int PIXELS = 64;
    private static final int TEXTURE_USAGE = GpuTexture.USAGE_COPY_DST
            | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_RENDER_ATTACHMENT;
    private static final IconCache<IconKey, Image> CACHE = new IconCache<>(128, Image::close);
    private static final ConcurrentLinkedQueue<RetiredTexture> RETIRED = new ConcurrentLinkedQueue<>();
    private static DirectContext owner;
    private static boolean failed;
    private static VertexConsumerProvider.Immediate immediate;
    private static RenderDispatcher dispatcher;
    private static ProjectionMatrix2 projection;
    private static KeyedItemRenderState itemState;
    private static GpuTexture depth;
    private static GpuTextureView depthView;

    private ItemIconRenderer() {
    }

    /**
     * Refreshes last frame's requested icons, including animations/custom models.
     * Textures are immutable generations: a borrowed image's texture is never
     * overwritten or deleted until Skia invokes its release callback.
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
            ensureResources();
            GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
            ProjectionType previousType = RenderSystem.getProjectionType();
            try {
                for (IconKey key : requests) {
                    ItemStack stack = key.stack();
                    if (!stack.isEmpty()) CACHE.put(key, render(stack, context));
                }
            } finally {
                RenderSystem.setProjectionMatrix(previousProjection, previousType);
            }
            releaseRetired();
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

    private static Image render(ItemStack stack, DirectContext context) {
        GpuTexture color = RenderSystem.getDevice().createTexture("atomchat item icon", TEXTURE_USAGE,
                TextureFormat.RGBA8, PIXELS, PIXELS, 1, 1);
        GpuTextureView colorView = RenderSystem.getDevice().createTextureView(color);
        boolean borrowed = false;
        GpuTextureView previousColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView previousDepth = RenderSystem.outputDepthTextureOverride;
        try {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorAndDepthTextures(color, 0, depth, 1.0);
            RenderSystem.outputColorTextureOverride = colorView;
            RenderSystem.outputDepthTextureOverride = depthView;
            RenderSystem.setProjectionMatrix(projection.set(PIXELS, PIXELS), ProjectionType.ORTHOGRAPHIC);
            MinecraftClient client = MinecraftClient.getInstance();
            // Vanilla resolves model overrides and native custom renderers, applies
            // GUI lighting, fullbright and foil.
            client.getItemModelManager().clearAndUpdate(itemState, stack,
                    ItemDisplayContext.GUI, client.world, client.player, 0);
            MatrixStack matrices = new MatrixStack();
            matrices.translate(PIXELS / 2.0F, PIXELS / 2.0F, 0.0F);
            matrices.scale(PIXELS, -PIXELS, PIXELS);
            client.gameRenderer.getDiffuseLighting().setShaderLights(itemState.isSideLit()
                    ? DiffuseLighting.Type.ITEMS_3D : DiffuseLighting.Type.ITEMS_FLAT);
            // 1.21.11 collects item draws into a command queue; replay it here so
            // special models (heads, shulker boxes) reach the offscreen texture too.
            itemState.render(matrices, dispatcher.getQueue(), 15728880, OverlayTexture.DEFAULT_UV, 0);
            dispatcher.render();
            immediate.draw();
            Image image;
            try (BackendTexture backend = BackendTexture.makeGL(PIXELS, PIXELS, false,
                    new GLTextureInfo(GL11C.GL_TEXTURE_2D, textureId(color), 0x8058))) {
                image = Image.borrowTextureFrom(context, backend, SurfaceOrigin.BOTTOM_LEFT,
                        ColorType.RGBA_8888, ColorAlphaType.PREMUL, null,
                        () -> RETIRED.add(new RetiredTexture(colorView, color)));
            }
            borrowed = true;
            return image;
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            if (!borrowed) {
                colorView.close();
                color.close();
            }
        }
    }

    private static void ensureResources() {
        if (immediate == null) {
            immediate = VertexConsumerProvider.immediate(new BufferAllocator(262144));
        }
        if (projection == null) {
            projection = new ProjectionMatrix2("atomchat item icon", -1000.0F, 1000.0F, true);
        }
        if (itemState == null) {
            itemState = new KeyedItemRenderState();
        }
        if (dispatcher == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            dispatcher = new RenderDispatcher(
                    new OrderedRenderCommandQueueImpl(),
                    client.getBlockRenderManager(),
                    immediate,
                    client.getAtlasManager(),
                    new OutlineVertexConsumerProvider(),
                    VertexConsumerProvider.immediate(new BufferAllocator(1536)),
                    client.textRenderer);
        }
        if (depth == null) {
            depth = RenderSystem.getDevice().createTexture("atomchat item icon depth",
                    GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, PIXELS, PIXELS, 1, 1);
            depthView = RenderSystem.getDevice().createTextureView(depth);
        }
    }

    private static void releaseRetired() {
        RetiredTexture retired;
        while ((retired = RETIRED.poll()) != null) {
            retired.view().close();
            retired.texture().close();
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

    private record RetiredTexture(GpuTextureView view, GpuTexture texture) {}

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
