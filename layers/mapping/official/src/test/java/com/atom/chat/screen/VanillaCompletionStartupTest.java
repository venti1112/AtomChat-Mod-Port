package com.atom.chat.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import com.atom.chat.ui.UiLayout;
import sun.misc.Unsafe;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real vanilla key handler, with constructor-only client dependencies excluded from the fixture. */
class VanillaCompletionStartupTest {
    static final class Owner extends Screen {
        net.minecraft.client.gui.components.events.GuiEventListener target;
        Owner() { super(Component.empty()); }
        @Override public net.minecraft.client.gui.components.events.GuiEventListener getFocused() { return target; }
    }
    static final class Engine extends CommandSuggestions {
        int refreshes, shows;
        Engine(Minecraft client,Screen owner,EditBox field,Font font) { super(client,owner,field,font,false,false,1,10,true,0); }
        @Override public void updateCommandInfo() { refreshes++; }
        @Override public void showSuggestions(boolean narration) { shows++; }
        public CommandSuggestions.SuggestionsList atomchat$getSuggestions() { return null; }
        public List<FormattedCharSequence> atomchat$getUsage() { return List.of(); }
        public void atomchat$setLineLimit(int limit) { }
    }
    static final class Input extends EditBox {
        Input(Font font) { super(font,0,0,0,0,Component.empty()); }
        public int atomchat$getMaxLength() {return (Integer)read(this,EditBox.class,"maxLength");}
        public String atomchat$getSuffix() { return (String)read(this,EditBox.class,"suggestion"); }
        @SuppressWarnings("unchecked") public java.util.function.BiFunction<String,Integer,FormattedCharSequence> atomchat$getFormatter() {
            return (java.util.function.BiFunction<String,Integer,FormattedCharSequence>)read(this,EditBox.class,"formatter");
        }
    }
    private static Object read(Object owner,Class<?> type,String name) {
        try {var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
        catch(Exception e) {throw new AssertionError(e);}
    }
    private static Unsafe unsafe() throws Exception {
        var field=Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return (Unsafe)field.get(null);
    }
    private static void put(Object owner,Class<?> type,String name,Object value) throws Exception {
        var field=type.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    @Test void nativeTabWorksImmediatelyAfterTheReplacementIsInstalled() throws Exception {
        var unsafe=unsafe(); var input=(Input)unsafe.allocateInstance(Input.class);
        put(input,EditBox.class,"value","/he");
        var owner=(Owner)unsafe.allocateInstance(Owner.class);owner.target=input;
        var engine=(Engine)unsafe.allocateInstance(Engine.class);
        put(engine,CommandSuggestions.class,"screen",owner);put(engine,CommandSuggestions.class,"input",input);
        try {CommandSuggestions.class.getMethod("setAllowHiding",boolean.class).invoke(engine,true);} catch(NoSuchMethodException old120) { /* 1.20 has no hiding flag. */ } // Restore the real constructor default skipped by allocation.
        var wiring=new SuggestorWiring(new SuggestorWiring.Host() {
            public EditBox input() { return input; }
            public UiLayout layout() { return UiLayout.of(0,0,420,780); }
            public double density() { return 1; }
            public double scaleFactor() { return 1; }
            public String suffix() {return input.atomchat$getSuffix();}
            public int maxLength() {return input.atomchat$getMaxLength();}
            public java.util.function.BiFunction<String,Integer,FormattedCharSequence> formatter() {return input.atomchat$getFormatter();}
        },()->engine,new SuggestorWiring.EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return null;}
            public List<FormattedCharSequence> usage() {return List.of();}
            public void lineLimit(int value) {try {put(engine,CommandSuggestions.class,"suggestionLineLimit",value);} catch(Exception e) {throw new AssertionError(e);}}
        });
        wiring.install(null,owner,null,"/he");
        assertTrue(wiring.keyPressed(258,0,0),"first Tab must reach vanilla even before a candidate window exists");
        assertEquals(1,engine.shows);
        assertEquals(1,engine.refreshes,"the opening draft must already have a pending completion request");
    }

    @Test void nativeSuffixVisibilityIncludesEmptyInputAndHonoursCursorAndLength() throws Exception {
        var unsafe=unsafe(); var input=(Input)unsafe.allocateInstance(Input.class);
        put(input,EditBox.class,"value","/he");
        var owner=(Owner)unsafe.allocateInstance(Owner.class);owner.target=input;
        var engine=(Engine)unsafe.allocateInstance(Engine.class);
        put(engine,CommandSuggestions.class,"screen",owner);put(engine,CommandSuggestions.class,"input",input);
        try {CommandSuggestions.class.getMethod("setAllowHiding",boolean.class).invoke(engine,true);} catch(NoSuchMethodException old120) { /* 1.20 has no hiding flag. */ } // Restore the real constructor default skipped by allocation.
        var wiring=new SuggestorWiring(new SuggestorWiring.Host() {
            public EditBox input() { return input; }
            public UiLayout layout() { return UiLayout.of(0,0,420,780); }
            public double density() { return 1; }
            public double scaleFactor() { return 1; }
            public String suffix() {return input.atomchat$getSuffix();}
            public int maxLength() {return input.atomchat$getMaxLength();}
            public java.util.function.BiFunction<String,Integer,FormattedCharSequence> formatter() {return input.atomchat$getFormatter();}
        },()->engine,new SuggestorWiring.EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return null;}
            public List<FormattedCharSequence> usage() {return List.of();}
            public void lineLimit(int value) {try {put(engine,CommandSuggestions.class,"suggestionLineLimit",value);} catch(Exception e) {throw new AssertionError(e);}}
        });
        put(input,EditBox.class,"maxLength",256);put(input,EditBox.class,"cursorPos",3);
        input.setSuggestion("lp");assertEquals("lp",wiring.ghostSuffix());
        put(input,EditBox.class,"cursorPos",1);assertNull(wiring.ghostSuffix());
        put(input,EditBox.class,"cursorPos",3);put(input,EditBox.class,"maxLength",3);assertNull(wiring.ghostSuffix());
        put(input,EditBox.class,"value","");put(input,EditBox.class,"cursorPos",0);put(input,EditBox.class,"maxLength",256);
        input.setSuggestion("Alice");assertEquals("Alice",wiring.ghostSuffix());
    }
    @Test void nativeParserFormattingKeepsLiteralCharactersAndPerGlyphColours() throws Exception {
        var unsafe=unsafe(); var input=(Input)unsafe.allocateInstance(Input.class);
        put(input,EditBox.class,"value","/he");
        var owner=(Owner)unsafe.allocateInstance(Owner.class);owner.target=input;
        var engine=(Engine)unsafe.allocateInstance(Engine.class);
        put(engine,CommandSuggestions.class,"screen",owner);put(engine,CommandSuggestions.class,"input",input);
        try {CommandSuggestions.class.getMethod("setAllowHiding",boolean.class).invoke(engine,true);} catch(NoSuchMethodException old120) { /* 1.20 has no hiding flag. */ } // Restore the real constructor default skipped by allocation.
        var wiring=new SuggestorWiring(new SuggestorWiring.Host() {
            public EditBox input() { return input; }
            public UiLayout layout() { return UiLayout.of(0,0,420,780); }
            public double density() { return 1; }
            public double scaleFactor() { return 1; }
            public String suffix() {return input.atomchat$getSuffix();}
            public int maxLength() {return input.atomchat$getMaxLength();}
            public java.util.function.BiFunction<String,Integer,FormattedCharSequence> formatter() {return input.atomchat$getFormatter();}
        },()->engine,new SuggestorWiring.EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return null;}
            public List<FormattedCharSequence> usage() {return List.of();}
            public void lineLimit(int value) {try {put(engine,CommandSuggestions.class,"suggestionLineLimit",value);} catch(Exception e) {throw new AssertionError(e);}}
        });
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<Object>();
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("help").then(
                com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object,Integer>argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer())));
        var format=CommandSuggestions.class.getDeclaredMethod("formatChat",String.class,int.class);format.setAccessible(true);
        java.util.function.BiFunction<String,Integer,FormattedCharSequence> provider=(part,offset)->{
            try {return (FormattedCharSequence)format.invoke(engine,part,offset);} catch(Exception e) {throw new AssertionError(e);}
        };
        put(input,EditBox.class,"formatter",provider);
        for(String line:List.of("/help 2","/help §c2")) {
            var reader=new com.mojang.brigadier.StringReader(line);reader.skip();
            put(engine,CommandSuggestions.class,"currentParse",dispatcher.parse(reader,new Object()));
            var expected=new StringBuilder();var colors=new java.util.ArrayList<Integer>();
            provider.apply(line,0).accept((index,style,cp)->{expected.appendCodePoint(cp);colors.add(style.getColor()==null ? -1 : style.getColor().getValue());return true;});
            var actual=wiring.formattedInputLine(line,0);var actualColors=new java.util.ArrayList<Integer>();
            for(var run:actual.runs())run.text().codePoints().forEach(cp->actualColors.add(run.color()==null ? -1 : run.color()));
            assertEquals(expected.toString(),actual.text());assertEquals(colors,actualColors);
            assertEquals(line,actual.text(),"native ordered text must not be parsed again as section-sign markup");
        }
    }
}
