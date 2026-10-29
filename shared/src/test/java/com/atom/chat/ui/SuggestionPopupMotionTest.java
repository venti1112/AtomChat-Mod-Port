package com.atom.chat.ui;

import io.github.humbleui.skija.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SuggestionPopupMotionTest {
    @BeforeAll static void setup(@TempDir Path root) throws Exception { UiFeatureRenderTest.setup(root); }
    @Test void completionUsesPopupEntranceHoverAndExitWhileKeepingCanvasBalanced() {
        var popup = new SuggestionPopup(); var labels = List.of("gamemode", "gamerule", "give").stream().map(com.atom.chat.render.FormattedLine::plain).toList();
        var geometry = SuggestionGeometry.of(UiLayout.of(15,15,420,780),3,180);
        try (var surface = Surface.makeRasterN32Premul(460,820)) {
            var canvas = surface.getCanvas(); int saves = canvas.getSaveCount(); canvas.clear(0);
            popup.draw(canvas,geometry,labels,0,0,-1,-1,1); var early = pixels(surface);
            canvas.clear(0); popup.draw(canvas,geometry,labels,0,0,-1,-1,200);
            assertFalse(java.util.Arrays.equals(early,pixels(surface)),"completion popup must animate like its composer siblings");
            var row = geometry.row(1);
            canvas.clear(0); popup.draw(canvas,geometry,labels,0,1,row.x()+30,row.y()+15,1); var hover = pixels(surface);
            canvas.clear(0); popup.draw(canvas,geometry,labels,0,1,row.x()+30,row.y()+15,90);
            assertFalse(java.util.Arrays.equals(hover,pixels(surface)),"hover must fade and scale rather than jump");
            canvas.clear(0); popup.draw(canvas,null,List.of(),0,-1,-1,-1,1);
            assertTrue(nonzero(pixels(surface)),"closing retains the outgoing content for the exit");
            canvas.clear(0); popup.draw(canvas,null,List.of(),0,-1,-1,-1,200);
            assertFalse(nonzero(pixels(surface))); assertEquals(saves,canvas.getSaveCount());
        }
    }
    private static boolean nonzero(byte[] pixels) { for(byte b:pixels)if(b!=0)return true;return false; }
    private static byte[] pixels(Surface surface) {
        try(var b=new Bitmap()){b.allocN32Pixels(460,820);assertTrue(surface.readPixels(b,0,0));return b.readPixels();}
    }
}
