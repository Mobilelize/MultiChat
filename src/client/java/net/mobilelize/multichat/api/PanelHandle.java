package net.mobilelize.multichat.api;

import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.ChatPanel;

/**
 * Handle to a chat panel created via {@link MultiChatApi#createPanel(String)} — lets a mod build and
 * fully configure its own panel (position, size, opacity, behavior) without the player ever needing to
 * open MultiChat's own Panel List screen.
 */
public class PanelHandle {

    private final ChatPanel panel;

    PanelHandle(ChatPanel panel) {
        this.panel = panel;
    }

    public int      getId()    { return panel.id(); }
    public String   getName()  { return panel.name(); }
    public ChatPanel getPanel() { return panel; }

    // ------------------------------------------------------------------
    // Identity / lifecycle
    // ------------------------------------------------------------------

    public PanelHandle setName(String name) {
        panel.setName(name);
        return this;
    }

    public PanelHandle setEnabled(boolean v) {
        panel.setEnabled(v);
        return this;
    }

    public boolean isEnabled() { return panel.isEnabled(); }

    /** Removes this panel entirely and cleans up every tab's (including sub-tabs') reference to it. */
    public void remove() {
        ChatManager.removePanel(panel);
    }

    // ------------------------------------------------------------------
    // Position & size
    // ------------------------------------------------------------------

    public PanelHandle setAnchor(ChatPanel.Anchor anchor) {
        panel.setAnchor(anchor);
        return this;
    }

    public ChatPanel.Anchor getAnchor() { return panel.anchor(); }

    /** Position for CUSTOM anchor — both fractions (0.0–1.0) of screen width/height. */
    public PanelHandle setPosition(float xFraction, float yFraction) {
        panel.setCustomX(xFraction);
        panel.setCustomY(yFraction);
        return this;
    }

    /** Pixel width at scale 1.0. -1 = inherit vanilla chat width. */
    public PanelHandle setWidth(int width) {
        panel.setCustomWidth(width);
        return this;
    }

    /** Visible line count while focused/open. -1 = inherit vanilla focused height. */
    public PanelHandle setFocusedHeightLines(int lines) {
        panel.setCustomHeightLines(lines);
        return this;
    }

    /** Visible line count while unfocused/closed, if not locked to the focused count. */
    public PanelHandle setUnfocusedHeightLines(int lines) {
        panel.setCustomUnfocusedHeightLines(lines);
        return this;
    }

    /** When true, unfocused height always matches the focused height instead of using its own value. */
    public PanelHandle setLockUnfocusedToFocused(boolean v) {
        panel.setLockUnfocusedToFocused(v);
        return this;
    }

    /** Render scale. -1 = inherit vanilla chat scale. */
    public PanelHandle setScale(float scale) {
        panel.setCustomScale(scale);
        return this;
    }

    public PanelHandle setMargin(int margin) {
        panel.setMargin(margin);
        return this;
    }

    // ------------------------------------------------------------------
    // Appearance
    // ------------------------------------------------------------------

    /** Message background color for tabs on this panel that haven't set their own. 0 = inherit theme default. */
    public PanelHandle setBgColor(int argbOrRgb) {
        panel.setBgColor(argbOrRgb);
        return this;
    }

    /** -1 = inherit the vanilla chat background opacity option. */
    public PanelHandle setBgOpacity(double opacity) {
        panel.setCustomBgOpacity(opacity);
        return this;
    }

    /** -1 = inherit the vanilla chat text opacity option. */
    public PanelHandle setChatOpacity(double opacity) {
        panel.setCustomChatOpacity(opacity);
        return this;
    }

    /** -1 = inherit the global/default tab background opacity. */
    public PanelHandle setTabBgOpacity(double opacity) {
        panel.setTabBgOpacity(opacity);
        return this;
    }

    /** -1 = inherit the global/default tab text opacity. */
    public PanelHandle setTabFgOpacity(double opacity) {
        panel.setTabFgOpacity(opacity);
        return this;
    }

    public PanelHandle setTabsAtTop(boolean v) {
        panel.setTabsAtTop(v);
        return this;
    }

    public PanelHandle setShowTabs(boolean v) {
        panel.setShowTabs(v);
        return this;
    }

    public PanelHandle setShowBadges(boolean v) {
        panel.setShowBadges(v);
        return this;
    }

    // ------------------------------------------------------------------
    // Behavior
    // ------------------------------------------------------------------

    /** Renders at full opacity/full line count permanently instead of only briefly after a new message. */
    public PanelHandle setAlwaysFocused(boolean v) {
        panel.setAlwaysFocused(v);
        return this;
    }

    public boolean getAlwaysFocused() { return panel.alwaysFocused(); }

    public PanelHandle setShowOnlyWhenFocused(boolean v) {
        panel.setShowOnlyWhenFocused(v);
        return this;
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    /** Adds a tab to this panel (in addition to any panels it's already on). */
    public PanelHandle addTab(TabHandle tab) {
        tab.getTab().visibleOnPanels().add(panel.id());
        return this;
    }

    /** Removes a tab from this panel only — the tab itself isn't deleted. */
    public PanelHandle removeTab(TabHandle tab) {
        tab.getTab().visibleOnPanels().remove(panel.id());
        return this;
    }
}
