package com.atom.chat.screen;

import net.minecraft.client.gui.components.CommandSuggestions;

/** 1.20 already gives Tab to completion; the optional hiding flag was added later. */
public final class CompletionVersionPolicy {
    private CompletionVersionPolicy() { }
    public static void configure(CommandSuggestions suggestions) { }
}
