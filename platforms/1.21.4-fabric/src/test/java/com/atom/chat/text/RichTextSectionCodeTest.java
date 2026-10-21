package com.atom.chat.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * The section-sign cases where the Fabric and Forge parsers disagreed. The
 * agreed reference is Forge behaviour, so these are the values both layers must
 * produce.
 *
 * <p>Why this exists: the older tests only covered "§aHello §rWorld" and
 * "§aA§cB", and both parsers happen to agree on those, so the drift below had
 * no guard. A parser that swallows an unrecognised code, drops a trailing lone
 * "§", or treats a colour code as an exclusive style reset passes those two
 * tests while rendering a different message than the Forge targets do.
 *
 * <p>The literal "§" is used rather than {@code Formatting.FORMATTING_CODE_PREFIX}
 * because it is what arrives from the server as chat content.
 */
class RichTextSectionCodeTest {

    @Test
    void unknownSectionCodeIsKeptLiterally() {
        RichText rich = RichText.of(Component.literal("A§zB"));

        assertEquals("A§zB", rich.getString());
        // The unrecognised pair is plain text in the style already in effect, so
        // it must not become a run of its own.
        assertEquals(1, rich.runs().size());
    }

    @Test
    void unknownSectionCodeKeepsTheColourInEffect() {
        RichText rich = RichText.of(Component.literal("§aA§zB"));

        assertEquals("A§zB", rich.getString());
        assertEquals(1, rich.runs().size());
        assertEquals(ChatFormatting.GREEN.getColor().intValue(),
                rich.runs().get(0).style().getColor().getValue());
    }

    @Test
    void loneTrailingSectionSignIsKept() {
        RichText rich = RichText.of(Component.literal("done§"));

        assertEquals("done§", rich.getString());
        assertEquals(1, rich.runs().size());
    }

    @Test
    void colourCodeKeepsTheFormatFlagsSetBeforeIt() {
        RichText rich = RichText.of(Component.literal("§l§aX"));

        assertEquals("X", rich.getString());
        assertEquals(1, rich.runs().size());
        Style style = rich.runs().get(0).style();
        assertEquals(ChatFormatting.GREEN.getColor().intValue(), style.getColor().getValue());
        assertTrue(style.isBold(), "a colour code must not clear the bold flag set before it");
    }

    @Test
    void formatCodeKeepsTheColourSetBeforeIt() {
        RichText rich = RichText.of(Component.literal("§a§lX"));

        assertEquals("X", rich.getString());
        assertEquals(1, rich.runs().size());
        Style style = rich.runs().get(0).style();
        assertEquals(ChatFormatting.GREEN.getColor().intValue(), style.getColor().getValue());
        assertTrue(style.isBold());
    }
}
