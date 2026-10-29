package com.atom.chat.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.function.BiFunction;

/** Read the native completion presentation without calculating a second suffix or parser. */
@Mixin(EditBox.class)
public interface ComposerSuggestionAccessor {
    @Accessor("suggestion") String atomchat$getSuffix();
    @Accessor("maxLength") int atomchat$getMaxLength();
    @Accessor("formatter") BiFunction<String,Integer,FormattedCharSequence> atomchat$getFormatter();
}
