package com.atom.chat.screen;

import net.minecraft.client.gui.components.CommandSuggestions;

/** Match the 1.21 vanilla chat screen's completion policy. */
public final class CompletionVersionPolicy {
    private CompletionVersionPolicy() { }
    public static void configure(CommandSuggestions suggestions) { suggestions.setAllowHiding(false); }
}
