package net.minecraft.client.gui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import com.atom.chat.ui.UiLayout;
import sun.misc.Unsafe;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real vanilla key handler, with constructor-only client dependencies excluded from the fixture. */
class VanillaCompletionStartupTest {
    static final class Owner extends Screen {
        net.minecraft.client.gui.Element target;
        Owner() { super(Text.empty()); }
        @Override public net.minecraft.client.gui.Element getFocused() { return target; }
    }
    static final class Engine extends ChatInputSuggestor {
        int refreshes, shows;
        Engine(MinecraftClient client,Screen owner,TextFieldWidget field,TextRenderer font) { super(client,owner,field,font,false,false,1,10,true,0); }
        @Override public void refresh() { refreshes++; }
        @Override public void show(boolean narration) { shows++; }
        public ChatInputSuggestor.SuggestionWindow atomchat$getWindow() { return null; }
        public List<OrderedText> atomchat$getUsage() { return List.of(); }
        public void atomchat$setLineLimit(int limit) { }
    }
    static final class Input extends TextFieldWidget {
        Input(TextRenderer font) { super(font,0,0,0,0,Text.empty()); }
        public int atomchat$getMaxLength() {return (Integer)read(this,TextFieldWidget.class,"maxLength");}
        public String atomchat$getSuffix() { return (String)read(this,TextFieldWidget.class,"suggestion"); }
        @SuppressWarnings("unchecked") public java.util.function.BiFunction<String,Integer,OrderedText> atomchat$getFormatter() {
            return (java.util.function.BiFunction<String,Integer,OrderedText>)read(this,TextFieldWidget.class,"renderTextProvider");
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
        put(input,TextFieldWidget.class,"text","/he");
        var owner=(Owner)unsafe.allocateInstance(Owner.class);owner.target=input;
        var engine=(Engine)unsafe.allocateInstance(Engine.class);
        put(engine,ChatInputSuggestor.class,"owner",owner);put(engine,ChatInputSuggestor.class,"textField",input);
        try {ChatInputSuggestor.class.getMethod("setCanLeave",boolean.class).invoke(engine,true);} catch(NoSuchMethodException old120) { /* 1.20 has no hiding flag. */ } // Restore the real constructor default skipped by allocation.
        var wiring=new SuggestorWiring(new SuggestorWiring.Host() {
            public TextFieldWidget input() { return input; }
            public UiLayout layout() { return UiLayout.of(0,0,420,780); }
            public double density() { return 1; }
            public double scaleFactor() { return 1; }
            public String suffix() {return input.atomchat$getSuffix();}
            public int maxLength() {return input.atomchat$getMaxLength();}
            public java.util.function.BiFunction<String,Integer,OrderedText> formatter() {return input.atomchat$getFormatter();}
        },()->engine,new SuggestorWiring.EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return null;}
            public List<OrderedText> usage() {return List.of();}
            public void lineLimit(int value) {try {put(engine,ChatInputSuggestor.class,"maxSuggestionSize",value);} catch(Exception e) {throw new AssertionError(e);}}
        });
        wiring.install(null,owner,null);
        assertTrue(wiring.keyPressed(258,0,0),"first Tab must reach vanilla even before a candidate window exists");
        assertEquals(1,engine.shows);
        assertEquals(1,engine.refreshes,"the opening draft must already have a pending completion request");
    }

    @Test void nativeSuffixVisibilityIncludesEmptyInputAndHonoursCursorAndLength() throws Exception {
        var unsafe=unsafe(); var input=(Input)unsafe.allocateInstance(Input.class);
        put(input,TextFieldWidget.class,"text","/he");
        var owner=(Owner)unsafe.allocateInstance(Owner.class);owner.target=input;
        var engine=(Engine)unsafe.allocateInstance(Engine.class);
        put(engine,ChatInputSuggestor.class,"owner",owner);put(engine,ChatInputSuggestor.class,"textField",input);
        try {ChatInputSuggestor.class.getMethod("setCanLeave",boolean.class).invoke(engine,true);} catch(NoSuchMethodException old120) { /* 1.20 has no hiding flag. */ } // Restore the real constructor default skipped by allocation.
        var wiring=new SuggestorWiring(new SuggestorWiring.Host() {
            public TextFieldWidget input() { return input; }
            public UiLayout layout() { return UiLayout.of(0,0,420,780); }
            public double density() { return 1; }
            public double scaleFactor() { return 1; }
            public String suffix() {return input.atomchat$getSuffix();}
            public int maxLength() {return input.atomchat$getMaxLength();}
            public java.util.function.BiFunction<String,Integer,OrderedText> formatter() {return input.atomchat$getFormatter();}
        },()->engine,new SuggestorWiring.EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return null;}
            public List<OrderedText> usage() {return List.of();}
            public void lineLimit(int value) {try {put(engine,ChatInputSuggestor.class,"maxSuggestionSize",value);} catch(Exception e) {throw new AssertionError(e);}}
        });
        put(input,TextFieldWidget.class,"maxLength",256);put(input,TextFieldWidget.class,"selectionStart",3);
        input.setSuggestion("lp");assertEquals("lp",wiring.ghostSuffix());
        put(input,TextFieldWidget.class,"selectionStart",1);assertNull(wiring.ghostSuffix());
        put(input,TextFieldWidget.class,"selectionStart",3);put(input,TextFieldWidget.class,"maxLength",3);assertNull(wiring.ghostSuffix());
        put(input,TextFieldWidget.class,"text","");put(input,TextFieldWidget.class,"selectionStart",0);put(input,TextFieldWidget.class,"maxLength",256);
        input.setSuggestion("Alice");assertEquals("Alice",wiring.ghostSuffix());
    }
    @Test void nativeParserFormattingKeepsLiteralCharactersAndPerGlyphColours() throws Exception {
        var unsafe=unsafe(); var input=(Input)unsafe.allocateInstance(Input.class);
        put(input,TextFieldWidget.class,"text","/he");
        var owner=(Owner)unsafe.allocateInstance(Owner.class);owner.target=input;
        var engine=(Engine)unsafe.allocateInstance(Engine.class);
        put(engine,ChatInputSuggestor.class,"owner",owner);put(engine,ChatInputSuggestor.class,"textField",input);
        try {ChatInputSuggestor.class.getMethod("setCanLeave",boolean.class).invoke(engine,true);} catch(NoSuchMethodException old120) { /* 1.20 has no hiding flag. */ } // Restore the real constructor default skipped by allocation.
        var wiring=new SuggestorWiring(new SuggestorWiring.Host() {
            public TextFieldWidget input() { return input; }
            public UiLayout layout() { return UiLayout.of(0,0,420,780); }
            public double density() { return 1; }
            public double scaleFactor() { return 1; }
            public String suffix() {return input.atomchat$getSuffix();}
            public int maxLength() {return input.atomchat$getMaxLength();}
            public java.util.function.BiFunction<String,Integer,OrderedText> formatter() {return input.atomchat$getFormatter();}
        },()->engine,new SuggestorWiring.EngineAccess() {
            public com.atom.chat.ui.CandidateWindow window() {return null;}
            public List<OrderedText> usage() {return List.of();}
            public void lineLimit(int value) {try {put(engine,ChatInputSuggestor.class,"maxSuggestionSize",value);} catch(Exception e) {throw new AssertionError(e);}}
        });
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<Object>();
        dispatcher.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal("help").then(
                com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object,Integer>argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer())));
        var format=ChatInputSuggestor.class.getDeclaredMethod("provideRenderText",String.class,int.class);format.setAccessible(true);
        java.util.function.BiFunction<String,Integer,OrderedText> provider=(part,offset)->{
            try {return (OrderedText)format.invoke(engine,part,offset);} catch(Exception e) {throw new AssertionError(e);}
        };
        put(input,TextFieldWidget.class,"renderTextProvider",provider);
        for(String line:List.of("/help 2","/help §c2")) {
            var reader=new com.mojang.brigadier.StringReader(line);reader.skip();
            put(engine,ChatInputSuggestor.class,"parse",dispatcher.parse(reader,new Object()));
            var expected=new StringBuilder();var colors=new java.util.ArrayList<Integer>();
            provider.apply(line,0).accept((index,style,cp)->{expected.appendCodePoint(cp);colors.add(style.getColor()==null ? -1 : style.getColor().getRgb());return true;});
            var actual=wiring.formattedInputLine(line,0);var actualColors=new java.util.ArrayList<Integer>();
            for(var run:actual.runs())run.text().codePoints().forEach(cp->actualColors.add(run.color()==null ? -1 : run.color()));
            assertEquals(expected.toString(),actual.text());assertEquals(colors,actualColors);
            assertEquals(line,actual.text(),"native ordered text must not be parsed again as section-sign markup");
        }
    }
}
