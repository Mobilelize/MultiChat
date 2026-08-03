package net.mobilelize.multichat.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registered chat themes + which one is active. Mods add their own via MultiChatApi.registerTheme. */
public final class ThemeRegistry {

    private ThemeRegistry() {}

    private static final Map<String, ChatTheme> themes = new LinkedHashMap<>();
    private static ChatTheme active;

    static {
        registerBuiltins();
    }

    public static void register(ChatTheme theme) {
        themes.put(theme.id, theme);
        if (active == null) active = theme;
    }

    /** The currently active theme — never null, always falls back to Vanilla. */
    public static ChatTheme active() {
        if (active == null) registerBuiltins(); // safety net in case something touches this before static init ran
        return active;
    }

    public static void setActive(String id) {
        ChatTheme t = themes.get(id);
        if (t == null) return;
        active = t;
        if (MultichatClient.CONFIG != null) {
            MultichatClient.CONFIG.activeThemeId = id;
            MultichatClient.saveTabsToConfig();
        }
    }

    public static ChatTheme get(String id) { return themes.get(id); }

    public static List<ChatTheme> getAll() { return new ArrayList<>(themes.values()); }

    private static void registerBuiltins() {
        if (themes.containsKey("vanilla")) return;

        ChatTheme vanilla = new ChatTheme("vanilla", "Vanilla");
        vanilla.tabBgActive            = Colors.TAB_BAR_NO_COLOR_ACTIVE;
        vanilla.tabBgInactive          = Colors.TAB_BAR_NO_COLOR_INACTIVE;
        vanilla.tabArrowText           = Colors.TAB_BAR_ARROW_TEXT;
        vanilla.tabCustomAlphaActive   = Colors.TAB_CUSTOM_ALPHA_ACTIVE;
        vanilla.tabCustomAlphaInactive = Colors.TAB_CUSTOM_ALPHA_INACTIVE;
        vanilla.tabTextActive          = Colors.TEXT_WHITE;
        vanilla.tabTextInactive        = Colors.TEXT_INACTIVE;
        vanilla.messageTextRgb         = Colors.MESSAGE_TEXT_RGB;
        vanilla.scrollbarUnreadRgb     = Colors.CHAT_SCROLLBAR_UNREAD_RGB;
        vanilla.scrollbarNoUnreadRgb   = Colors.CHAT_SCROLLBAR_NO_UNREAD_RGB;
        vanilla.scrollbarHighlightRgb  = Colors.CHAT_SCROLLBAR_HIGHLIGHT_RGB;
        vanilla.scrollbarActiveRgb     = Colors.CHAT_SCROLLBAR_ACTIVE_RGB;
        vanilla.scrollbarGlow          = Colors.CHAT_SCROLLBAR_GLOW;
        vanilla.highlightSearchRgb     = Colors.HIGHLIGHT_SEARCH_RGB;
        vanilla.highlightNewRgb        = Colors.HIGHLIGHT_NEW_RGB;
        vanilla.highlightFlashRgb      = Colors.HIGHLIGHT_FLASH_RGB;
        vanilla.selection              = Colors.SELECTION;
        vanilla.defaultPanelBg         = 0; // unset — keeps the original black/no-override look
        register(vanilla);

        ChatTheme simpleDark = new ChatTheme("simple_dark", "Simple Dark");
        simpleDark.tabBgActive            = 0xEE2B2B2B;
        simpleDark.tabBgInactive          = 0xAA1A1A1A;
        simpleDark.tabArrowText           = 0xFFAAAAAA;
        simpleDark.tabCustomAlphaActive   = 0xDD000000;
        simpleDark.tabCustomAlphaInactive = 0x66000000;
        simpleDark.tabTextActive          = 0xFFEEEEEE;
        simpleDark.tabTextInactive        = 0xFF888888;
        simpleDark.messageTextRgb         = 0xEEEEEE;
        simpleDark.scrollbarUnreadRgb     = 0xFF6688;
        simpleDark.scrollbarNoUnreadRgb   = 0x555555;
        simpleDark.scrollbarHighlightRgb  = 0x888888;
        simpleDark.scrollbarActiveRgb     = 0xFFFFFF;
        simpleDark.scrollbarGlow          = 0x40AACCFF;
        simpleDark.highlightSearchRgb     = Colors.HIGHLIGHT_SEARCH_RGB;
        simpleDark.highlightNewRgb        = Colors.HIGHLIGHT_NEW_RGB;
        simpleDark.highlightFlashRgb      = Colors.HIGHLIGHT_FLASH_RGB;
        simpleDark.selection              = 0x773388CC;
        simpleDark.defaultPanelBg         = 0;
        register(simpleDark);


        ChatTheme bold = new ChatTheme("bold", "Bold");
        bold.tabBgActive            = -869651916;
        bold.tabBgInactive          = 1714039348;
        bold.tabArrowText           = -1426063361;
        bold.tabCustomAlphaActive   = Colors.TAB_CUSTOM_ALPHA_ACTIVE;
        bold.tabCustomAlphaInactive = Colors.TAB_CUSTOM_ALPHA_INACTIVE;
        bold.tabTextActive          = -1;
        bold.tabTextInactive        = -1426063361;
        bold.messageTextRgb         = 0xFFFFFF;
        // RGB-only (no alpha byte) — the renderer adds alpha with `+ (alpha << 24)`, so a value with
        // its own alpha already set (like pasting Staff Mod's raw ARGB constants directly) would
        // overflow/corrupt on top of that instead of just looking wrong. Values below are Staff Mod's
        // originals with the alpha byte stripped: -43691 (0xFFFF5555, badge red) → 0xFF5555; and
        // -1426063361 (0xAAFFFFFF, its actual white *thumb*) → 0xFFFFFF — 1426063360 (0x55000000) was
        // Staff Mod's near-black *track*, not the thumb, and doesn't have a slot here since this
        // renderer never draws a separate track, only the thumb.
        bold.scrollbarUnreadRgb     = 0xFF5555;
        bold.scrollbarNoUnreadRgb   = 0xFFFFFF;
        bold.scrollbarHighlightRgb  = 0xFFFFFF;
        bold.scrollbarActiveRgb     = 0xFFFFFF;
        bold.scrollbarGlow          = 0x40FFFFFF;
        bold.highlightSearchRgb     = Colors.HIGHLIGHT_SEARCH_RGB;
        bold.highlightNewRgb        = Colors.HIGHLIGHT_NEW_RGB;
        bold.highlightFlashRgb      = Colors.HIGHLIGHT_FLASH_RGB;
        bold.selection              = Colors.SELECTION;
        bold.defaultPanelBg         = 0;
        register(bold);

        // Pastel trio — spread the "rainbow" across separate themes (pink/mint/lavender) rather than
        // one theme with mixed hues, so each still reads as a coherent palette. Bg is a dark, translucent
        // tint of the hue (same pattern as Bold/Simple Dark) instead of a light pastel fill — light bg +
        // white text was unreadable, and light bg + dark text looked out of place next to the rest of
        // the mod's dark-chrome look. White/light text stays legible against the dark tint either way.
        ChatTheme pink = new ChatTheme("pink", "Pink");
        pink.tabBgActive          = 0xEE402030;
        pink.tabBgInactive        = 0xAA2A1620;
        pink.tabArrowText         = 0xFFFFD9E3;
        pink.tabCustomAlphaActive   = Colors.TAB_CUSTOM_ALPHA_ACTIVE;
        pink.tabCustomAlphaInactive = Colors.TAB_CUSTOM_ALPHA_INACTIVE;
        pink.tabTextActive        = 0xFFFFFFFF;
        pink.tabTextInactive      = 0xFFE8C2CE;
        pink.messageTextRgb       = 0xFFF5F7;
        pink.scrollbarUnreadRgb   = 0xFF8FA3;
        pink.scrollbarNoUnreadRgb = 0xFFB3C6;
        pink.scrollbarHighlightRgb= 0xFFE0EA;
        pink.scrollbarActiveRgb   = 0xFFFFFF;
        pink.scrollbarGlow        = 0x40FFB3C6;
        pink.highlightSearchRgb   = Colors.HIGHLIGHT_SEARCH_RGB;
        pink.highlightNewRgb      = Colors.HIGHLIGHT_NEW_RGB;
        pink.highlightFlashRgb    = Colors.HIGHLIGHT_FLASH_RGB;
        pink.selection            = 0x77FF99C2;
        pink.defaultPanelBg       = 0x2A1620;
        register(pink);

        ChatTheme mint = new ChatTheme("mint", "Mint");
        mint.tabBgActive          = 0xEE1F4A35;
        mint.tabBgInactive        = 0xAA14301F;
        mint.tabArrowText         = 0xFFD9FFEA;
        mint.tabCustomAlphaActive   = Colors.TAB_CUSTOM_ALPHA_ACTIVE;
        mint.tabCustomAlphaInactive = Colors.TAB_CUSTOM_ALPHA_INACTIVE;
        mint.tabTextActive        = 0xFFFFFFFF;
        mint.tabTextInactive      = 0xFFC2E8D3;
        mint.messageTextRgb       = 0xF5FFF9;
        mint.scrollbarUnreadRgb   = 0xFF8FA3;
        mint.scrollbarNoUnreadRgb = 0xB3FFD9;
        mint.scrollbarHighlightRgb= 0xE0FFF0;
        mint.scrollbarActiveRgb   = 0xFFFFFF;
        mint.scrollbarGlow        = 0x40B3FFD9;
        mint.highlightSearchRgb   = Colors.HIGHLIGHT_SEARCH_RGB;
        mint.highlightNewRgb      = Colors.HIGHLIGHT_NEW_RGB;
        mint.highlightFlashRgb    = Colors.HIGHLIGHT_FLASH_RGB;
        mint.selection            = 0x7799FFC2;
        mint.defaultPanelBg       = 0x14301F;
        register(mint);

        ChatTheme lavender = new ChatTheme("lavender", "Lavender");
        lavender.tabBgActive          = 0xEE3D2B5C;
        lavender.tabBgInactive        = 0xAA281C3D;
        lavender.tabArrowText         = 0xFFE8D9FF;
        lavender.tabCustomAlphaActive   = Colors.TAB_CUSTOM_ALPHA_ACTIVE;
        lavender.tabCustomAlphaInactive = Colors.TAB_CUSTOM_ALPHA_INACTIVE;
        lavender.tabTextActive        = 0xFFFFFFFF;
        lavender.tabTextInactive      = 0xFFD3C2E8;
        lavender.messageTextRgb       = 0xF9F5FF;
        lavender.scrollbarUnreadRgb   = 0xFF8FA3;
        lavender.scrollbarNoUnreadRgb = 0xD4B3FF;
        lavender.scrollbarHighlightRgb= 0xEDE0FF;
        lavender.scrollbarActiveRgb   = 0xFFFFFF;
        lavender.scrollbarGlow        = 0x40D4B3FF;
        lavender.highlightSearchRgb   = Colors.HIGHLIGHT_SEARCH_RGB;
        lavender.highlightNewRgb      = Colors.HIGHLIGHT_NEW_RGB;
        lavender.highlightFlashRgb    = Colors.HIGHLIGHT_FLASH_RGB;
        lavender.selection            = 0x77B399FF;
        lavender.defaultPanelBg       = 0x281C3D;
        register(lavender);

        if (MultichatClient.CONFIG != null) {
            ChatTheme saved = themes.get(MultichatClient.CONFIG.activeThemeId);
            if (saved != null) active = saved;
        }
    }
}
