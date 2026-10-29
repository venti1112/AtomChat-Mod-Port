package com.atom.chat.fun;

import com.atom.chat.AtomChat;
import io.github.humbleui.skija.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Same-frame framebuffer sampling only: no GPU image survives a context reset. */
public final class ExperimentalEffectRenderer implements AutoCloseable {
    private RuntimeEffect effect;
    private boolean failed,closed;
    private final ByteBuffer uniforms=ByteBuffer.allocate(20).order(ByteOrder.nativeOrder());
    public void draw(Surface surface,ExperimentalEffectType type,float progress) {
        draw(surface,type,progress,false);
    }
    /** Final display RGB is already composited; shaderpacks may leave its alpha undefined. */
    public void drawDisplayFramebuffer(Surface surface,ExperimentalEffectType type,float progress) {
        draw(surface,type,progress,true);
    }
    private void draw(Surface surface,ExperimentalEffectType type,float progress,boolean displayRgb) {
        if(closed||failed||type==null||!(progress>0&&progress<1))return;
        var canvas=surface.getCanvas();int saves=canvas.getSaveCount();
        try {
            if(effect==null)effect=RuntimeEffect.makeForShader(ExperimentalEffectShader.SOURCE);
            uniforms.clear();uniforms.putFloat(surface.getWidth()).putFloat(surface.getHeight()).putFloat(progress).putFloat(type.ordinal()).putFloat(displayRgb?1:0);
            try(var image=surface.makeImageSnapshot();var child=image.makeShader(FilterTileMode.CLAMP,FilterTileMode.CLAMP,SamplingMode.LINEAR,null);
                var data=Data.makeFromBytes(uniforms.array());var shader=effect.makeShader(data,new Shader[]{child});
                var paint=new Paint().setShader(shader).setBlendMode(BlendMode.SRC)) {
                canvas.save();canvas.resetMatrix();
                canvas.drawRect(io.github.humbleui.types.Rect.makeWH(surface.getWidth(),surface.getHeight()),paint);
            }
        } catch(RuntimeException failure) {
            failed=true;
            AtomChat.LOGGER.warn(com.atom.chat.diagnostics.AtomChatLog.message("fun","Experimental SkSL playback disabled for this screen"),failure);
        } finally { canvas.restoreToCount(saves); }
    }
    @Override public void close(){closed=true;if(effect!=null){effect.close();effect=null;}}
}
