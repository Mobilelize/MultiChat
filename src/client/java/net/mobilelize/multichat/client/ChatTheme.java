package net.mobilelize.multichat.client;

/**
 * A swappable color palette for chat rendering. Fields match exactly the colors ChatRenderer reads —
 * tab bar, message text, scrollbar, and message-line highlight overlays. Colors-only for now; shape/
 * geometry (corner radii, borders) isn't themeable since the renderer doesn't draw any of that yet.
 */
public final class ChatTheme {

    public final String id;
    public String displayName;

    // Tab bar
    public int tabBgActive;
    public int tabBgInactive;
    public int tabArrowText;
    public int tabCustomAlphaActive;   // OR'd onto a tab's own custom RGB color (tab.tabColor())
    public int tabCustomAlphaInactive;
    public int tabTextActive;
    public int tabTextInactive;

    // Message text
    public int messageTextRgb;

    /** Default panel background RGB (0 = no override, matches vanilla's current unset-black look).
     *  Used as the panel's message-row background whenever the panel itself hasn't set a custom BG color. */
    public int defaultPanelBg;

    // Scrollbar
    public int scrollbarUnreadRgb;
    public int scrollbarNoUnreadRgb;
    public int scrollbarHighlightRgb;
    public int scrollbarActiveRgb;
    public int scrollbarGlow;

    // Message-line highlight overlays
    public int highlightSearchRgb;
    public int highlightNewRgb;
    public int highlightFlashRgb;
    public int selection;

    public ChatTheme(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
}
