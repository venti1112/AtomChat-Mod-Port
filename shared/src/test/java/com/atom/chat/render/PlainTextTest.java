package com.atom.chat.render;

import com.atom.chat.font.FontManager;
import com.atom.chat.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PlainTextTest {
    @BeforeAll static void setup(@TempDir Path root) {
        Platform.install(new Platform.Provider() {
            public Path configDir(){return root.resolve("config");}
            public Path gameDir(){return root.resolve("game");}
            public boolean isModLoaded(String id){return false;}
        });
    }
    @Test void editableSectionSignsAndSpacesKeepTheirNativeSourceOffsets() {
        var font=FontManager.font(25);String source="/help  §c  你好 😀 abc";
        var lines=PlainText.wrap(font,source,PlainText.width(font,"/help "));
        assertTrue(lines.size()>1);assertEquals(source,String.join("",lines));
        assertTrue(PlainText.width(font,"§c")>SkiaFontRenderer.getStringWidth(font,"§c"));
        for(String line:lines) {
            assertFalse(Character.isHighSurrogate(line.charAt(line.length()-1)));
            assertFalse(Character.isLowSurrogate(line.charAt(0)));
        }
    }
    @Test void formattedNativeRunsDoNotReinterpretLiteralSectionSigns() {
        var b=new FormattedLine.Builder();"§c".codePoints().forEach(cp->b.append(cp,0x55FFFF,true));
        var line=b.finish();assertEquals("§c",line.text());assertEquals(1,line.runs().size());
        assertEquals(0x55FFFF,line.runs().get(0).color());assertTrue(line.runs().get(0).underline());
    }
}
