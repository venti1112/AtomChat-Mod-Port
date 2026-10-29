package com.atom.chat.ui;

/** Placement inherited from the shipped emoji/quick-phrase popups; one origin and bottom gap. */
public final class ComposerPopupLayout {
    private ComposerPopupLayout() { }
    public static float left(UiLayout layout) { return layout.inputBar.x(); }
    public static float top(UiLayout layout, float height) { return bottom(layout) - height; }
    public static float bottom(UiLayout layout) { return layout.inputBar.y() - UiTokens.COMPOSER_POPUP_GAP; }
    public static UiLayout.Rect place(UiLayout layout, float width, float height) {
        return new UiLayout.Rect(left(layout), top(layout, height), width, height);
    }
}
