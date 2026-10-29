package com.atom.chat.fun;

import com.atom.chat.config.AtomChatConfig;
import com.atom.chat.settings.*;
import com.atom.chat.ui.SettingsGroupGlyphs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentalEffectsTest {
    private AtomChatConfig cfg;
    @BeforeEach void setup(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        com.atom.chat.platform.Platform.install(new com.atom.chat.platform.Platform.Provider() {
            public java.nio.file.Path configDir(){return root.resolve("config");}
            public java.nio.file.Path gameDir(){return root;}
            public boolean isModLoaded(String id){return false;}
        });
        cfg=new AtomChatConfig();
        var field=AtomChatConfig.class.getDeclaredField("instance");field.setAccessible(true);field.set(null,cfg);
    }
    @Test void defaultOffAndActualAdvancedFoldHasOnlyOneSwitchImmediatelyAfterFun() throws Exception {
        assertFalse(AtomChatConfig.class.getField("experimentalEffectsEnabled").getBoolean(cfg));
        var page=new SettingsSectionPage();
        int advanced=0;for(var chip:page.chips(SettingsSection.CHAT)) { if(chip.id().equals("advanced"))break;advanced++; }
        page.selectChip(SettingsSection.CHAT,advanced);
        var rows=page.rows(SettingsSection.CHAT);int fun=-1,experimental=-1;
        for(int i=0;i<rows.size();i++) {
            if("atomchat.settings.group.chat.fun".equals(rows.get(i).labelKey()))fun=i;
            if("atomchat.settings.group.chat.experimental".equals(rows.get(i).labelKey()))experimental=i;
        }
        assertTrue(fun>=0);assertEquals(fun+2,experimental);
        assertEquals("experimental_effects",rows.get(experimental+1).item().id());
        assertEquals(rows.size()-1,experimental+1);
        assertSame(SettingsGroupGlyphs.EXPERIMENTAL,SettingsGroupIcons.forLabel("atomchat.settings.group.chat.experimental"));
        var gson=new com.google.gson.Gson();
        assertFalse(gson.fromJson("{}",AtomChatConfig.class).experimentalEffectsEnabled,"old config files default off");
        var item=rows.get(experimental+1).item();item.set(true);
        var saved=com.atom.chat.platform.Platform.configDir().resolve("atomchat/atomchat-client.json");
        assertTrue(gson.fromJson(java.nio.file.Files.readString(saved),AtomChatConfig.class).experimentalEffectsEnabled);
    }
    @Test void onlyManualEnableStartsIgnitionAndBusyMessagesDoNotQueue() {
        try(var effects=new ExperimentalEffects(false)) {
            assertFalse(effects.advance(false,true,100));
            assertTrue(effects.advance(true,true,200));assertEquals(ExperimentalEffectType.IGNITION,effects.type());
            effects.onMessage("冷死了",300);assertEquals(ExperimentalEffectType.IGNITION,effects.type());
            assertFalse(effects.advance(true,true,3200));
            effects.onMessage("冷死了",3300);assertEquals(ExperimentalEffectType.FROST,effects.type());
            assertTrue(effects.advance(true,true,3400));assertFalse(effects.advance(false,true,3500));
            assertTrue(effects.advance(true,true,3600));assertEquals(ExperimentalEffectType.IGNITION,effects.type());
        }
        try(var effects=new ExperimentalEffects(true)) {
            assertFalse(effects.advance(true,true,100));
            assertFalse(effects.advance(false,false,200));assertFalse(effects.advance(true,false,300));
            assertFalse(effects.advance(true,true,400),"enabling motion later must not replay a skipped activation");
        }
    }
    @Test void keywordWhitelistIgnoresCommandsLinksCodeAndQuotedText() {
        var matcher=new ExperimentalEffectMatcher();
        assertEquals(ExperimentalEffectType.WAVE,matcher.match("震惊！").orElseThrow());
        assertEquals(ExperimentalEffectType.FROST,matcher.match("冷死了 ❄").orElseThrow());
        assertEquals(ExperimentalEffectType.GRAVITY,matcher.match("黑洞 🕳").orElseThrow());
        assertTrue(matcher.match("/say 黑洞").isEmpty());assertTrue(matcher.match("`黑洞` https://example.com/震惊").isEmpty());
        assertTrue(matcher.match("「引用 @Bob: 冷死了」").isEmpty());assertTrue(matcher.match("coldplay shockwaves").isEmpty());
        assertEquals(ExperimentalEffectType.FROST,matcher.match("冷死了，然后黑洞").orElseThrow());
    }
    @Test void returningFromAnotherScreenReinstallsAWorkingSessionWithoutAnIgnitionReplay() throws Exception {
        cfg.experimentalEffectsEnabled=true;cfg.animationEnabled=true;
        var presentation=new ChatEffectPresentation((m,p)->true);
        var field=ChatEffectPresentation.class.getDeclaredField("experimental");field.setAccessible(true);
        presentation.install();var original=(ExperimentalEffects)field.get(presentation);
        presentation.close();presentation.install();
        try {
            var restored=(ExperimentalEffects)field.get(presentation);
            assertFalse(restored.advance(true,true,100),"restoring an existing enabled screen must not replay activation");
            restored.onMessage("黑洞",200);
            assertEquals(ExperimentalEffectType.GRAVITY,restored.type(),"HUD editor returns to the same ChatScreen instance");
            assertNotSame(original,restored,"closed native renderers must not be reused");
        } finally {presentation.close();}
    }
}
