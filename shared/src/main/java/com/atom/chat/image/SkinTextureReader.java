package com.atom.chat.image;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.system.MemoryUtil;

/** Reads real texture dimensions before allowing the driver to write client memory. */
public final class SkinTextureReader {
    private static final int GRID_SIZE = 64;
    private static final int MAX_SIZE = 4096;

    private SkinTextureReader() {}

    /** Returns a 64x64 RGBA skin grid, or null for unsupported texture geometry. */
    public static ByteBuffer read(int texture) {
        int binding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
        try {
            int width = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH);
            int height = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT);
            if (width < GRID_SIZE || width > MAX_SIZE || width % GRID_SIZE != 0
                    || (height != width && height * 2L != width)) return null;

            int alignment = GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT);
            int rowLength = GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH);
            int skipRows = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS);
            int skipPixels = GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS);
            int packBuffer = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
            ByteBuffer raw = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            try {
                // Foreign renderers can leave row padding, skips or a PBO active.
                // Those settings change the destination address and required capacity.
                GL21C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
                GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
                GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
                GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
                GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
                GL11C.glGetTexImage(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, raw);

                ByteBuffer grid = ByteBuffer.allocate(GRID_SIZE * GRID_SIZE * 4);
                int scale = width / GRID_SIZE;
                for (int y = 0; y < height / scale; y++) {
                    for (int x = 0; x < GRID_SIZE; x++) {
                        int src = (y * scale * width + x * scale) * 4;
                        int dst = (y * GRID_SIZE + x) * 4;
                        for (int channel = 0; channel < 4; channel++) {
                            grid.put(dst + channel, raw.get(src + channel));
                        }
                    }
                }
                return grid;
            } finally {
                GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, alignment);
                GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, rowLength);
                GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, skipRows);
                GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, skipPixels);
                GL21C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBuffer);
                MemoryUtil.memFree(raw);
            }
        } finally {
            // No Blaze3D cache changes: raw binding is restored before any game code runs.
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, binding);
        }
    }
}
