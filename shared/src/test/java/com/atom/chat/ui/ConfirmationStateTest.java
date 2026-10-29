package com.atom.chat.ui;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ConfirmationStateTest {
    @Test void openingAndCancellingNeverRunTheAction() {
        var state = new ConfirmationState();
        var calls = new AtomicInteger();
        state.open(calls::incrementAndGet);
        assertTrue(state.active());
        assertFalse(state.confirmFocused());
        state.cancel(); state.confirm();
        assertEquals(0, calls.get());
    }
    @Test void confirmationRunsOnceAndReopeningDropsTheOldCallback() {
        var state = new ConfirmationState();
        var old = new AtomicInteger(); var current = new AtomicInteger();
        state.open(old::incrementAndGet); state.open(current::incrementAndGet);
        state.confirm(); state.confirm();
        assertEquals(0, old.get()); assertEquals(1, current.get());
        assertFalse(state.active());
    }
    @Test void keyboardDefaultsToCancelAndTabExplicitlySelectsConfirm() {
        var state = new ConfirmationState(); var calls = new AtomicInteger();
        state.open(calls::incrementAndGet); state.key(257);
        assertEquals(0, calls.get()); assertFalse(state.active());
        state.open(calls::incrementAndGet); state.key(258); state.key(257);
        assertEquals(1, calls.get());
        state.open(calls::incrementAndGet); state.key(256);
        assertEquals(1, calls.get()); assertFalse(state.active());
    }
}
