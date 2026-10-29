package com.atom.chat.ui;

import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComposerPopupTransformTest {
    @Test void pointerInverseMatchesTheActualCanvasMatrixDuringEntryAndExit() {
        var box=new UiLayout.Rect(30,200,300,240);
        try(var surface=Surface.makeRasterN32Premul(500,700)) {
            var canvas=surface.getCanvas();
            for(float progress:new float[]{0,.15F,.5F,.9F,1}) {
                var pose=ComposerPopupTransform.of(box,progress);canvas.save();pose.apply(canvas);
                float[] m=canvas.getLocalToDeviceAsMatrix33().getMat();
                float x=box.x()+2,y=box.bottom()-2;
                float sx=m[0]*x+m[1]*y+m[2],sy=m[3]*x+m[4]*y+m[5];
                var restored=pose.toLocal(sx,sy);
                assertEquals(x,restored.x(),.001F);assertEquals(y,restored.y(),.001F);
                canvas.restore();
            }
        }
    }
}
