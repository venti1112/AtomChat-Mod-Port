package com.atom.chat.mixin;

import net.minecraft.client.util.math.Rect2i;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public interface SuggestionWindowAccessor extends com.atom.chat.ui.CandidateWindow {
    @Mutable
    @Final
    @Accessor("area")
    Rect2i atomchat$getArea();

    @Mutable
    @Final
    @Accessor("area")
    void atomchat$setArea(Rect2i area);

    @Accessor("suggestions")
    java.util.List<com.mojang.brigadier.suggestion.Suggestion> atomchat$getCandidates();
    @Accessor("inWindowIndex")
    int atomchat$getOffset();
    @Accessor("inWindowIndex")
    void atomchat$setOffset(int offset);
    @Accessor("selection")
    int atomchat$getSelected();
    @org.spongepowered.asm.mixin.gen.Invoker("select")
    void atomchat$select(int index);
    @org.spongepowered.asm.mixin.gen.Invoker("complete")
    void atomchat$complete();
}
