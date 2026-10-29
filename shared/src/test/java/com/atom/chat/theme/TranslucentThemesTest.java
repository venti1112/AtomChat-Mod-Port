package com.atom.chat.theme;

import com.atom.chat.config.AtomChatConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslucentThemesTest {
    @Test void fourAdditionalPalettesHaveVisibleTransparencyAndBlurWithoutChangingLayout() {
        assertEquals(12, ThemeService.presets().length);
        for (String id : new String[]{"azure_glass", "plum_glass", "ice_glass", "sand_glass"}) {
            assertNotNull(ThemeService.byId(id), id);
            var config = new AtomChatConfig(); config.panelWidth = 470;
            ThemeService.apply(config, id);
            assertTrue(config.panelOpacity >= .6F && config.panelOpacity <= .8F, id);
            assertTrue(config.blurEnabled, id);
            assertEquals(470, config.panelWidth);
            assertEquals(id, config.themeName);
        }
    }
}
