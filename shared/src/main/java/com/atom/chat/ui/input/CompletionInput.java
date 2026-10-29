package com.atom.chat.ui.input;

/** Completion gets vanilla's first chance before an editor's Esc/Enter handling. */
public final class CompletionInput implements InputHandler {
    public interface Host {
        boolean available();
        boolean click(double x, double y, int button);
        boolean scroll(double x, double y, double amount);
        boolean key(int key, int scan, int modifiers);
    }
    private final Host host;
    public CompletionInput(Host host) { this.host = host; }
    @Override public boolean onClick(double x, double y, int button) { return host.available() && host.click(x, y, button); }
    @Override public boolean onScroll(double x, double y, double horizontal, double vertical) { return host.available() && host.scroll(x, y, vertical); }
    @Override public boolean onKey(int key, int scan, int modifiers) { return host.available() && host.key(key, scan, modifiers); }
}
