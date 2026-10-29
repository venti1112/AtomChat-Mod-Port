package com.atom.chat.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.function.BiFunction;

/** Read the native completion presentation without calculating a second suffix or parser. */
@Mixin(TextFieldWidget.class)
public interface ComposerSuggestionAccessor {
    @Accessor("suggestion") String atomchat$getSuffix();
    @Accessor("maxLength") int atomchat$getMaxLength();
    @Accessor("renderTextProvider") BiFunction<String,Integer,OrderedText> atomchat$getFormatter();
}
