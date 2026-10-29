package com.atom.chat.fun;

import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentalEffectRendererTest {
    @Test void translucentSourceKeepsItsAlphaAndPremultipliedRgb() {
        try(var surface=Surface.makeRasterN32Premul(160,90);var renderer=new ExperimentalEffectRenderer();var bitmap=new Bitmap()) {
            surface.getCanvas().clear(0x4026384C);renderer.draw(surface,ExperimentalEffectType.IGNITION,.43f);
            bitmap.allocN32Pixels(160,90);assertTrue(surface.readPixels(bitmap,0,0));var bytes=bitmap.readPixels();
            for(int i=0;i<bytes.length;i+=4) {
                int alpha=bytes[i+3]&255;assertEquals(64,alpha,"a captured scene must replace, not blend over itself");
                for(int c=0;c<3;c++)assertTrue((bytes[i+c]&255)<=alpha,"shader output must be premultiplied");
            }
        }
    }
    @Test void lensCoreIsDarkAndWaveInteriorNeverBecomesASolidFlash() {
        try(var surface=Surface.makeRasterN32Premul(320,180);var renderer=new ExperimentalEffectRenderer()) {
            surface.getCanvas().clear(0xFF26384C);renderer.draw(surface,ExperimentalEffectType.GRAVITY,.43f);
            int core=pixel(surface,160,90);
            assertTrue((core&255)<50&&((core>>>16)&255)<30,"lens core is dark, not a saturated procedural fill");
            surface.getCanvas().clear(0xFF26384C);renderer.draw(surface,ExperimentalEffectType.WAVE,.43f);
            int interior=pixel(surface,160,90);
            assertTrue((interior&255)<110&&((interior>>>8)&255)<90,"the expanding wave is a ring, not a filled disk");
        }
    }
    @Test void realShadersAlterBothScreenEdgesRestoreStateAndReturnExactEndpoints() throws Exception {
        try(var surface=Surface.makeRasterN32Premul(320,180);var renderer=new ExperimentalEffectRenderer();var paint=new Paint()) {
            var canvas=surface.getCanvas();
            for(var type:ExperimentalEffectType.values()) {
                background(canvas,paint);int original=pixel(surface,20,20);
                canvas.save();canvas.scale(1.7f,1.7f);int saves=canvas.getSaveCount();var matrix=canvas.getLocalToDeviceAsMatrix33();
                renderer.draw(surface,type,0);assertEquals(original,pixel(surface,20,20));
                renderer.draw(surface,type,.43f);
                assertEquals(saves,canvas.getSaveCount());assertEquals(matrix,canvas.getLocalToDeviceAsMatrix33());
                assertNotEquals(original,pixel(surface,20,20),type+" must cover beyond the chat panel");
                assertNotEquals(0xFF26384C,pixel(surface,300,160),type+" must reach the other screen edge");
                canvas.restore();
                background(canvas,paint);renderer.draw(surface,type,1);assertEquals(original,pixel(surface,20,20));
            }
        }
    }
    @Test void exportActualNativeShaderFrames() throws Exception {
        var directory=java.nio.file.Path.of("").toAbsolutePath();
        while(!java.nio.file.Files.isDirectory(directory.resolve("shared")))directory=directory.getParent();
        directory=directory.resolve(".work/experimental/frames");java.nio.file.Files.createDirectories(directory);
        try(var surface=Surface.makeRasterN32Premul(640,360);var renderer=new ExperimentalEffectRenderer();var paint=new Paint()) {
            for(var type:ExperimentalEffectType.values())for(float t:new float[]{.18f,.43f,.72f}) {
                background(surface.getCanvas(),paint);renderer.draw(surface,type,t);
                try(var image=surface.makeImageSnapshot();var data=image.encodeToData()) {
                    java.nio.file.Files.write(directory.resolve(type.name().toLowerCase()+"-"+Math.round(t*100)+".png"),data.getBytes());
                }
            }
        }
    }
    private static void background(Canvas canvas,Paint paint) {
        canvas.clear(0xFF26384C);
        for(int x=0;x<640;x+=40)canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(x,0,2,360),paint.setColor(0xFF506070));
        for(int y=0;y<360;y+=40)canvas.drawRect(io.github.humbleui.types.Rect.makeXYWH(0,y,640,2),paint.setColor(0xFF506070));
    }
    private static int pixel(Surface surface,int x,int y) {
        try(var bitmap=new Bitmap()){bitmap.allocN32Pixels(1,1);assertTrue(surface.readPixels(bitmap,x,y));return bitmap.getColor(0,0);}
    }
}
