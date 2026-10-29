package com.atom.chat.testing;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * 1.21.8's {@code ClickEvent.Action} static init pulls in the registries, so
 * every test touching a click action needs the vanilla bootstrap first.
 * Running it here keeps that independent of test class execution order.
 */
public final class VanillaBootstrapExtension implements BeforeAllCallback {
    private static boolean bootstrapped;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (bootstrapped) {
            return;
        }
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        bootstrapped = true;
    }
}
