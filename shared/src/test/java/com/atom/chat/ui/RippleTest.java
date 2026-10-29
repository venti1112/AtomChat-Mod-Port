package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RippleTest {
    @Test void repeatedClicksAreBoundedAndDisabledMotionClearsThem() {
        Ripple ripple = new Ripple();
        for (int i = 0; i < 100; i++) ripple.start(10, 20, true);
        assertEquals(Ripple.MAX_WAVES, ripple.size());
        ripple.update(16, false);
        assertEquals(0, ripple.size());
    }
    @Test void wavesExpandFadeAndExpireUsingFrameTime() {
        Ripple ripple = new Ripple();
        ripple.start(10, 20, true);
        ripple.update(Ripple.DURATION_MS / 2, true);
        assertTrue(ripple.progress(0) > 0 && ripple.progress(0) < 1);
        ripple.update(Ripple.DURATION_MS, true);
        assertEquals(0, ripple.size());
        ripple.start(1, 1, false);
        assertEquals(0, ripple.size());
    }
}
