package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzMutableText;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InfoTab extends ChatTab {

    public InfoTab(Collection<Integer> visibleOnPanels) {
        super("Info", TabType.INFO, visibleOnPanels);
    }

    public void rebuildMessages() {
        clearMessages();
        for (ChatPanel panel : ChatManager.getAllActivePanels()) panel.clearTabLines(this);

        for (LzText t : content()) {
            LzChatEntry entry = new LzChatEntry(0, t, null);
            MessageEntry me = new MessageEntry(t, t, entry);
            addFirst(me);
            for (ChatPanel panel : ChatManager.getAllActivePanels()) panel.addTabMessage(this, me);
        }
    }

    private static List<LzText> content() {
        List<LzText> lines = new ArrayList<>();

        // ── Footer  (top of page — seen when scrolled all the way up) ─────────
        lines.add(LzText.format("  &8Scroll &7↓ &8to return to the start."));
        lines.add(rainbowTitle());
        lines.add(blank());

        // ── Hide & Disable ────────────────────────────────────────────────────
        lines.add(sectionHeader("►", "&c", "HIDE & DISABLE"));
        lines.add(blank());
        lines.add(LzText.empty()
            .append(LzText.format("  "))
            .append(dangerLink("[ × Hide This Panel ]", "/multichat info panel hide",
                "Disables Panel 2 entirely.")));
        lines.add(LzText.empty()
            .append(LzText.format("  &7Show it again: "))
            .append(cmdLinkNoSpaces("/multichat info panel show", "")));
        lines.add(LzText.empty()
            .append(LzText.format("  &7Hide info tab only: "))
            .append(link("[ Hide Info Tab ]", "/multichat info hide", "Remove this tab from Panel 2.")));
        lines.add(LzText.empty()
            .append(LzText.format("  &7Show info tab: "))
            .append(cmdLinkNoSpaces("/multichat info show", "")));
        lines.add(LzText.format("  &7Disable mod:"));
        lines.add(LzText.empty()
            .append(LzText.format("    &81. "))
            .append(link("Open Settings", "/multichat settings", "Open MultiChat settings."))
            .append(LzText.format("  &82. &7Go to &fAdvanced  &83. &7Toggle &fDisable MultiChat")));
        lines.add(blank());

        // ── Tips ──────────────────────────────────────────────────────────────
        lines.add(sectionHeader("◆", "&d", "GOOD TO KNOW"));
        lines.add(blank());
        lines.add(tip("Panel 2 inherits vanilla chat size by default.",   "Change it in panel settings."));
        lines.add(tip("Pings must be enabled per-tab, not just globally.", "Off by default on new tabs."));
        lines.add(tip("Collapse only merges duplicates within the same tab.", "Not across tabs."));
        lines.add(tip("Search is temporary.",                              "Opening a new search replaces the old one."));
        lines.add(tip("Difference mode is permanent.",                    "Disabling a tab won't recover its messages elsewhere."));
        lines.add(tip("Filtered tabs can be refiltered.",                 "Open tab settings to reapply filters to message history."));
        lines.add(tip("Context prefix shows in the chat bar.",            "Disable the display in settings if preferred."));
        lines.add(tip("Context prefix has two modes.",                    "Command mode runs as a command. Text mode sends as a normal message."));
        lines.add(tip("Tab badge unread count clears when you view that tab.", ""));
        lines.add(tip("Scroll freeze while selecting text is a setting.", "New messages won't auto-scroll while you select."));
        lines.add(tip("Cursor changes based on what's under it.", "Hand over tabs and links. Text beam over selectable chat."));
        lines.add(tip("Hold Ctrl to force text-select mode.", "Cursor switches to IBeam even over clickable links."));
        lines.add(tip("Cursor changing can be disabled in settings.", ""));
        lines.add(tip("Highlighted messages can be reverted.", "Click a highlighted message to undo its formatting."));
        lines.add(tip("A multi-tab's \"All\" sub-tab can't be deleted.", "It always catches everything the parent lets through."));
        lines.add(tip("Popped-out tabs can be closed again.", "Right-click it → Close Popout."));
        lines.add(tip("Style Rules check rendering, not text.", "Bold/italic/underline/strikethrough/color — separate from normal filter rules."));
        lines.add(tip("Leave a Style Rule's Word field blank to skip content matching.", "Checks just the formatting at that location instead of a specific word."));
        lines.add(tip("Chat Heads mod support is automatic.", "Both Before Name and Before Line render modes work with no setup — see Help for details."));
        lines.add(blank());

        // ── Commands ──────────────────────────────────────────────────────────
        lines.add(sectionHeader("◆", "&3", "COMMANDS"));
        lines.add(tip("/mlchat is a short alias for /multichat", ""));
        lines.add(blank());
        lines.add(cmdLink("/multichat",            "Open tab list"));
        lines.add(cmdLink("/multichat settings",   "Open global settings"));
        lines.add(cmdLink("/multichat panels",     "Manage panels"));
        lines.add(cmdLink("/multichat layout",     "Move and resize panels in a GUI"));
        lines.add(cmdLink("/multichat help",       "Open full help guide"));
        lines.add(cmdLink("/multichat info show",        "Show this tab on Panel 2"));
        lines.add(cmdLink("/multichat info panel hide",  "Disable Panel 2"));
        lines.add(cmdLink("/multichat info panel show",  "Enable Panel 2"));
        lines.add(blank());
        lines.add(cmdLink("/multichat tab",                "Open tab list"));
        lines.add(cmdLink("/multichat tab add",            "Add a new filtered tab"));
        lines.add(cmdLink("/multichat tab remove",         "Remove the active tab"));
        lines.add(cmdLink("/multichat tab edit <name>",    "Open settings for a specific tab"));
        lines.add(blank());
        lines.add(cmdLink("/multichat context <tab>",      "Route typed chat through a tab's prefix"));
        lines.add(cmdLink("/multichat context",            "Clear active context"));
        lines.add(blank());
        lines.add(cmdLink("/multichat note",               "Open active note tab editor"));
        lines.add(cmdLink("/multichat note add <text>",    "Append text to active note tab"));
        lines.add(cmdLink("/multichat note open <name>",   "Open a specific note tab"));
        lines.add(blank());
        lines.add(cmdLink("/multichat quickactions",       "Open quick actions overlay"));
        lines.add(cmdLink("/multichat quickactions edit",  "Edit quick actions"));
        lines.add(blank());

        // ── Navigation ────────────────────────────────────────────────────────
        lines.add(sectionHeader("◆", "&6", "NAVIGATION"));
        lines.add(blank());
        lines.add(nav("Ctrl+Arrow",  "Switch focused panel"));
        lines.add(nav("Arrow keys",  "Switch tabs within focused panel"));
        lines.add(nav("Ctrl+F",      "Open search overlay"));
        lines.add(nav("Scroll",      "Scroll chat in hovered panel"));
        lines.add(nav("Right-click", "Copy  ·  Search  ·  Delete  ·  Delete from All"));
        lines.add(nav("Click a multi-tab", "Drill into its sub-tabs"));
        lines.add(nav("Red X / right-click sub-tab", "Collapse Sub-tabs (back out)"));
        lines.add(nav("Ctrl+Q",      "Open quick actions menu"));
        lines.add(blank());

        // ── Features ──────────────────────────────────────────────────────────
        lines.add(sectionHeader("✦", "&e", "FEATURES"));
        lines.add(blank());
        lines.add(feat("Collapse Repeating", "&7Merge duplicate messages with a count"));
        lines.add(feat("Timestamps",         "&7Show arrival time on each message"));
        lines.add(feat("Pings & Sounds",     "&7Play a sound when a tab gets a match"));
        lines.add(feat("Difference Mode",    "&7Only catch what no other tab matched"));
        lines.add(feat("Message Cap",        "&7Limit messages stored per tab"));
        lines.add(feat("Context Prefix",     "&7Auto-prepend text to your chat input"));
        lines.add(feat("Tab Badges",         "&7Show unread count on each tab"));
        lines.add(feat("Quick Actions",      "&7Right-click messages for instant commands"));
        lines.add(feat("Selection & Copy",   "&7Select and copy chat text like a browser"));
        lines.add(feat("Tab Colors",         "&7Color-code tabs and message backgrounds"));
        lines.add(feat("Multi-panel",        "&7Two independent chat windows side by side"));
        lines.add(feat("Multi-Tab",          "&7Nested sub-tabs, each with their own filter rules"));
        lines.add(feat("Highlight Words",    "&7Keyword messages reformatted into two colors"));
        lines.add(feat("Chat Themes",        "&7Vanilla, Simple Dark, Bold, Pink, Mint, Lavender"));
        lines.add(feat("Draggable Scrollbar", "&7Click and drag to scroll, glows on hover"));
        lines.add(feat("Always Focused Panel", "&7Keep a panel fully visible permanently"));
        lines.add(feat("Pop Out Tabs",       "&7Turn a tab into its own floating panel"));
        lines.add(feat("Style Rules",        "&7Whitelist/blacklist by actual rendered formatting, not text"));
        lines.add(feat("Chat Heads Support", "&7Works with both Chat Heads render modes automatically"));
        lines.add(blank());

        // ── Tab Types ─────────────────────────────────────────────────────────
        lines.add(sectionHeader("◆", "&5", "TAB TYPES"));
        lines.add(blank());
        lines.add(tabType("ALL",      "&f",  "Receives every message. Read-only."));
        lines.add(tabType("VANILLA",  "&7",  "Standard Minecraft chat, full vanilla behavior."));
        lines.add(tabType("FILTERED", "&b",  "Filter by keyword, regex, or indicator type. Can have sub-tabs."));
        lines.add(tabType("API",      "&5",  "Controlled by other mods via the MultiChat API."));
        lines.add(tabType("SEARCH",   "&e",  "Live-search through any tab's message history. Modes: CONTAINS, STARTS_WITH, WORD, FUZZY, REGEX. Updates live as new messages arrive; opening a new search replaces this tab."));
        lines.add(tabType("NOTES",    "&a",  "A personal scratchpad — write and save notes."));
        lines.add(tabType("INFO",     "&d",  "This tab. Quick-reference guide."));
        lines.add(blank());

        // ── Quick Start ───────────────────────────────────────────────────────
        lines.add(sectionHeader("►", "&a", "QUICK START"));
        lines.add(blank());
        lines.add(LzText.format("    &a&l1.&r &fOpen the config:"));
        lines.add(LzText.empty()
            .append(LzText.format("       "))
            .append(link("► Settings", "/multichat settings", "Open MultiChat settings.")));
        lines.add(blank());
        lines.add(LzText.format("    &a&l2.&r &fAdd a &bFiltered &ftab — enter a keyword to catch."));
        lines.add(LzText.empty()
            .append(LzText.format("       "))
            .append(link("► Open Tab List", "/multichat", "Open the tab list to add a new tab.")));
        lines.add(blank());
        lines.add(LzText.format("    &a&l3.&r &fMessages matching your keyword route to that tab."));
        lines.add(LzText.format("    &a&l4.&r &fCustomize: colors, pings, timestamps, collapse."));
        lines.add(LzText.format("    &a&l5.&r &fAdd more tabs and configure panels to your liking."));
        lines.add(blank());

        // ── What is MultiChat ─────────────────────────────────────────────────
        lines.add(sectionHeader("◆", "&b", "WHAT IS MULTICHAT"));
        lines.add(blank());
        lines.add(LzText.format("  &fMultiChat adds a second chat panel to the right side"));
        lines.add(LzText.format("  &fof your screen. Each panel shows its own set of tabs."));
        lines.add(blank());
        lines.add(LzText.format("  &7Filter, route, organize, and get notified — all without"));
        lines.add(LzText.format("  &7a separate overlay. Just smarter use of the chat you have."));
        lines.add(blank());

        // ── Header  (bottom — first visible when the tab opens) ───────────────
        lines.add(LzText.empty()
            .append(LzText.format("  "))
            .append(link("► Settings", "/multichat settings", "Open MultiChat settings."))
            .append(LzText.format("   "))
            .append(link("► Panels", "/multichat panels", "Configure panels."))
            .append(LzText.format("   "))
            .append(link("► Layout", "/multichat layout", "Move and resize panels."))
            .append(LzText.format("   "))
            .append(link("► Help", "/multichat help", "Open the full help guide.")));
        lines.add(blank());
        lines.add(rainbowTitle());
        lines.add(LzText.format("  &8Scroll &7↑ &8to read the full guide."));

        return lines;
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private static LzText blank() {
        return LzText.empty();
    }

    private static LzMutableText sectionHeader(String symbol, String color, String text) {
        return LzText.format("  " + color + "&l" + symbol + " " + text + "&r");
    }

    private static LzText cmdLink(String command, String desc) {
        LzText hover = LzText.literal("Click to suggest command")
            .withStyle(LzStyle.ofFormatting(LzFormatting.GRAY));
        LzMutableText cmdText = LzText.format("    &3" + command);
        cmdText.withStyle(LzStyle.empty()
            .withClickSuggestCommand(command)
            .withHoverText(hover));
        return LzText.empty().append(cmdText).append(LzText.format("  &7" + desc));
    }

    private static LzText cmdLinkNoSpaces(String command, String desc) {
        LzText hover = LzText.literal("Click to suggest command")
                .withStyle(LzStyle.ofFormatting(LzFormatting.GRAY));
        LzMutableText cmdText = LzText.format("&3" + command);
        cmdText.withStyle(LzStyle.empty()
                .withClickSuggestCommand(command)
                .withHoverText(hover));
        return LzText.empty().append(cmdText).append(LzText.format("  &7" + desc));
    }

    private static LzText tip(String fact, String detail) {
        String suffix = detail.isEmpty() ? "" : " &7" + detail;
        return LzText.format("  &d• &d" + fact + suffix);
    }

    private static LzText nav(String key, String desc) {
        return LzText.format("    &6" + key + "  &7→ &f" + desc);
    }

    private static LzText feat(String name, String coloredDesc) {
        return LzText.format("  &8• &e" + name + "  " + coloredDesc);
    }

    private static LzText tabType(String type, String color, String desc) {
        return LzText.format("    " + color + "&l" + type + "&r  &7" + desc);
    }

    private static LzMutableText rainbowTitle() {
        return LzText.format("  &6&l✦&r ")
            .append(LzText.format("&c&lM&r"))
            .append(LzText.format("&6&lu&r"))
            .append(LzText.format("&e&ll&r"))
            .append(LzText.format("&a&lt&r"))
            .append(LzText.format("&b&li&r"))
            .append(LzText.format("&3&lC&r"))
            .append(LzText.format("&9&lh&r"))
            .append(LzText.format("&5&la&r"))
            .append(LzText.format("&d&lt&r"))
            .append(LzText.format("  &6&l✦&r"));
    }

    private static LzMutableText dangerLink(String label, String command, String tooltip) {
        LzText hoverText = LzText.literal(tooltip)
            .withStyle(LzStyle.ofFormatting(LzFormatting.RED));
        LzMutableText t = LzText.literal(label);
        t.withStyle(LzStyle.empty()
            .withColor(0xFF5555)
            .withBold(true)
            .withClickRunCommand(command)
            .withHoverText(hoverText));
        return t;
    }

    private static LzMutableText link(String label, String command, String tooltip) {
        LzText hoverText = LzText.literal(tooltip)
            .withStyle(LzStyle.ofFormatting(LzFormatting.GRAY));
        LzMutableText t = LzText.literal(label);
        t.withStyle(LzStyle.empty()
            .withColor(Colors.TEXT_LINK)
            .withUnderline(true)
            .withClickRunCommand(command)
            .withHoverText(hoverText));
        return t;
    }
}
