package com.atom.chat.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets AtomChatSuggestor reposition the package-private SuggestionsList rect. */
@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionWindowAccessor extends com.atom.chat.ui.CandidateWindow {
    @Accessor("rect")
    Rect2i atomchat$getRect();

    @Mutable
    @Final
    @Accessor("rect")
    void atomchat$setRect(Rect2i rect);

    @Accessor("suggestionList")
    java.util.List<com.mojang.brigadier.suggestion.Suggestion> atomchat$getCandidates();
    @Accessor("offset")
    int atomchat$getOffset();
    @Accessor("offset")
    void atomchat$setOffset(int offset);
    @Accessor("current")
    int atomchat$getSelected();
    @org.spongepowered.asm.mixin.gen.Invoker("select")
    void atomchat$select(int index);
    @org.spongepowered.asm.mixin.gen.Invoker("useSuggestion")
    void atomchat$complete();
}
