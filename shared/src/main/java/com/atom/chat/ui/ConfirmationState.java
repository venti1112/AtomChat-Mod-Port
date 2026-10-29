package com.atom.chat.ui;

/** Owns the pending action. Clear it before invoking user code so duplicate events cannot repeat deletion. */
public final class ConfirmationState {
    private Runnable pending;
    private boolean confirmFocused;
    public boolean active() { return pending != null; }
    public boolean confirmFocused() { return confirmFocused; }
    public void open(Runnable action) { pending = java.util.Objects.requireNonNull(action); confirmFocused = false; }
    public void cancel() { pending = null; confirmFocused = false; }
    public void confirm() { Runnable action = pending; cancel(); if (action != null) action.run(); }
    public void key(int key) {
        if (!active()) return;
        if (key == 256) cancel();
        else if (key == 258 || key == 263 || key == 262) confirmFocused = !confirmFocused;
        else if (key == 257 || key == 335 || key == 32) { if (confirmFocused) confirm(); else cancel(); }
    }
}
