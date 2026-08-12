package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.text.LzText;

public class HelpScreen {

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(LzText.literal("MultiChat — Help").mc())
                .setSavingRunnable(() -> {});

        ConfigEntryBuilder eb = builder.entryBuilder();
        helpCategory(builder, eb);

        return builder.build();
    }

    public static void helpCategory(ConfigBuilder builder, ConfigEntryBuilder eb) {
        ConfigCategory help = builder.getOrCreateCategory(LzText.literal("Help").mc());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lCommands&r
                          &7Alias: &f/mlchat&r&7 works the same as &f/multichat&r
                          &f/multichat&r&7                          Open tab list
                          &f/multichat tab&r&7                      Open tab list
                          &f/multichat tab add&r&7                  Add a new filtered tab (auto-named)
                          &f/multichat tab add &b<name>&r&7          Add a new filtered tab with a name
                          &f/multichat tab remove&r&7               Remove the active tab on the focused panel
                          &f/multichat tab edit &b<name>&r&7          Open settings for a specific tab
                          &f/multichat panels&r&7                   Open panel list
                          &f/multichat panels &b<name>&r&7           Open panel settings
                          &f/multichat layout&r&7                   Open panel layout editor
                          &f/multichat settings&r&7                 Open global settings
                          &f/multichat quickactions&r&7             Open quick actions overlay
                          &f/multichat quickactions edit&r&7        Edit quick actions
                          &f/multichat context&r&7                  Clear chat context
                          &f/multichat context &b<tab>&r&7           Route typed chat to tab's prefix
                          &f/multichat note&r&7                     Open active note tab editor
                          &f/multichat note add &b<text>&r&7         Append text to active note tab
                          &f/multichat note open &b<name>&r&7        Open a specific note tab editor
                          &f/multichat highlight add &b<word>&r&7      Add a keyword to Your Keywords
                          &f/multichat highlight remove &b<word>&r&7   Remove a keyword from Your Keywords
                          &f/multichat highlight exception add &b<word>&r&7      Add a word to Your Exceptions
                          &f/multichat highlight exception remove &b<word>&r&7   Remove a word from Your Exceptions
                          &f/multichat highlight api exception add &b<word>&r&7    Add a word to API Exceptions
                          &f/multichat highlight api exception remove &b<word>&r&7 Remove a word from API Exceptions
                          &f/multichat info show&r&7                 Show the info tab on Panel 2
                          &f/multichat info hide&r&7                 Hide the info tab from Panel 2
                          &f/multichat help&r&7                     Open this screen"""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lTabs&r
                        &7Tabs organize chat into views. Each panel shows a list of tabs at the bottom (or top).
                          &f[All]&r&7  Receives every message. Read-only.
                          &f[Vanilla]&r&7  The default Minecraft chat. Only visible on the main panel.
                          &f[Filtered]&r&7  User-defined tab. Shows messages matching its whitelist/blacklist rules.
                          &f[Search]&r&7  Temporary tab created by a search query. Replaced when a new search runs.
                          &f[API]&r&7  Controlled by another mod via the MultiChat API.
                          &f[Notes]&r&7  Personal scratchpad. Write and save notes. Never receives routed messages.
                          &f[Info]&r&7  This quick-reference guide. Show/hide with &f/multichat info show&r&7 / &fhide&r&7."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lTab List Indicators&r
                        &7Shown after the tab name in the Tabs list screen.
                          &fWL:n&r&7 — whitelist rule count   &fBL:n&r&7 — blacklist rule count
                          &fT&r&7 — timestamps on   &fC&r&7 — collapse repeating on
                          &fD&r&7 — difference mode on (layer 0)   &fD5&r&7 / &fD-2&r&7 — difference mode, non-zero layer
                          &fE&r&7 — difference exception on   &fI&r&7 — difference inclusion on
                          &fB&r&7 — badge hidden   &fP&r&7 — context prefix set   &fS&r&7 — sound ping on
                          &f[multi]&r&7 — this tab has sub-tabs, listed indented (&f↳&r&7) directly under it"""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lPanels&r
                        &7Panels are independent chat windows. The &fmain panel&r&7 is always on the left.
                        Additional panels can be positioned anywhere on screen.
                        Open &fMod Menu → MultiChat → Panels&r&7 or run &f/multichat panels&r&7 to manage panels.
                        Use &fPanel Layout&r&7 to drag and resize panels visually with snap-to-grid.
                        &fArrow keys&r&7 switch tabs on the focused panel. &fCtrl+Arrow&r&7 cycles the focused panel.
                        Each panel can have a &fPanel Context Tab&r&7 — set in Panel → Tabs → &fSet CTX&r&7.
                        When &fPanel Context on Switch&r&7 is enabled, focusing a panel via Ctrl+Arrow auto-activates its context tab."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lFilters (Whitelist / Blacklist)&r
                        &7Filtered tabs use rules to decide which messages they receive.
                          &fWhitelist&r&7 — message must match at least one rule (or all, if mode is ALL).
                          &fBlacklist&r&7 — matching messages are excluded even if whitelisted.
                        Rule types: &fCONTAINS, STARTS_WITH, ENDS_WITH, REGEX, EXACT.&r
                        &7Indicator filter: &fANY, CHAT, SYSTEM&r&7, etc. — filters by message source type."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lStyle Rules (Whitelist / Blacklist)&r
                        &7Tab Edit → &fStyle Whitelist&r&7 / &fStyle Blacklist&r&7. Checks how part of a message
                        actually &frenders&r&7 — bold, italic, underline, strikethrough, color — not its text.
                        Separate from the text Filters above; a message must satisfy both (if either has rules).
                          &fTarget&r&7 — &fWORD&r&7 checks a specific word's style. &fSTART_OF_MESSAGE&r&7 /
                            &fEND_OF_MESSAGE&r&7 check the first/last word's style.
                          &fWord Location&r&7 (WORD only) — &fANYWHERE&r&7 searches for the word anywhere in the
                            message. &fSTART/END/INDEX&r&7 pin it to the first/last/Nth word instead.
                          &7Leave &fWord&r&7 blank to skip content matching and just check formatting at that
                            location — for &fANYWHERE&r&7 this checks the whole message's style instead of a
                            specific word.
                          &fFormatting&r&7 — toggle every style that must be present at once (bold AND italic).
                          &fColors&r&7 / &fHex Colors&r&7 — every character in range must be one of the toggled
                            or added colors, not necessarily the same one throughout, so a multi-colored word
                            can still match as long as every color it uses is in one of the two lists.
                        &7Example: require a &f[S]&r&7 sender prefix to actually render italic before treating
                        it as genuine staff chat — plain text alone can be spoofed by anyone typing it."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lDifference Mode&r
                        &7Routes messages through a priority pipeline based on the active tab on each enabled panel.
                          &fLayer index&r&7 — higher number = higher priority. Tabs at the same index share a layer.
                          &fPipeline order&r&7 — highest layer checked first. If any tab at that layer matches,
                            all matching tabs at that layer receive the message and the pipeline stops.
                            Lower layers never see that message.
                          &fNon-diff tab wins&r&7 — if the active tab on any panel is a normal (non-diff) filtered tab
                            and it matches, the entire diff pipeline is suppressed.
                          &fDifference Exception (E)&r&7 — tab receives messages normally but never blocks the pipeline.
                          &fDifference Inclusion (I)&r&7 — tab blocks the diff pipeline even when not the active tab,
                            as long as it is assigned to any enabled panel.
                        Layers use the &factive tab per panel&r&7 — switching panels changes which tabs participate.
                        Negative layer indices are allowed (useful for low-priority catch-all tabs)."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lContext Tab&r
                        &7Right-click a tab → &fToggle Context&r&7 to make it active, or use &f/multichat context <tab>&r&7.
                        While a context tab is set, messages you send are prefixed automatically.
                          &fContext Prefix&r&7 — text prepended to your message (set in Tab Edit).
                          &fCommand Mode ON&r&7 — sent as a command (e.g. prefix &f/tell Steve&r&7 + your message).
                          &fCommand Mode OFF&r&7 — sent as plain chat text.
                        The active context is shown above the chat input bar.
                        &fPanel Context&r&7 — assign a context tab to a panel in Panel → Tabs → &fSet CTX&r&7.
                        Switching to that panel via &fCtrl+Arrow&r&7 then auto-activates its context tab."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lText Selection & Search&r
                        &7Click and drag in chat to select text. &fCtrl+C&r&7 copies the selection.
                        &fCtrl+F&r&7 (or your configured key) opens Search. With text selected, it pre-fills the query.
                        Right-click any message for: &fCopy, Search for this, Jump to All, Delete, Delete from All.&r
                          &7&fDelete&r&7 removes it from the tab you clicked in. &fDelete from All&r&7 removes it
                          everywhere it appears — every tab, including multi-tab sub-tabs.
                        &7Search supports &fCONTAINS, STARTS_WITH, WORD, FUZZY, REGEX&r&7 modes and live updates as new messages arrive.
                        Hold &fCtrl&r&7 to force text-select mode — cursor switches to IBeam even over clickable links.

                        &e&lCursor&r
                        &7The cursor changes based on what's under it (can be disabled in settings).
                          &fHand&r&7 — over tabs, clickable text, or context menus.
                          &fIBeam&r&7 — over selectable message text, or any text when Ctrl is held."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lMulti-Tab (Sub-Tabs)&r
                        &7Any filtered tab can become a container for nested sub-tabs.
                          &fEnable&r&7 — Tab Edit → &fMulti Tab&r&7 toggle. Auto-creates a protected &fAll&r&7 sub-tab.
                          &fAdd more&r&7 — Tab Edit → &fSub-Tabs&r&7 list. One name per line; drag to reorder.
                          &fDrill in&r&7 — click the parent tab (marked with a symbol) in the tab bar to swap the
                            tab row to its sub-tabs. Clicking a sub-tab shows only its own messages.
                          &fBack out&r&7 — click the red &fX&r&7 at the left of the sub-tab row, or right-click any
                            sub-tab → &fCollapse Sub-tabs&r&7.
                        &7Filtering is layered: a sub-tab only ever sees messages its parent's own filter already
                        let through. The &fAll&r&7 sub-tab catches everything the parent lets through, unfiltered;
                        other sub-tabs narrow further from there using their own whitelist/blacklist rules —
                        edit a sub-tab's rules the same way as any tab: right-click it → &fEdit Tab&r&7."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lHighlight Words&r
                        &7Settings → &fHighlight Words&r&7. When a keyword matches, the &fentire message&r&7 is
                        reformatted into two colors — one for the matched keyword(s), one for the rest.
                          &fYour Keywords&r&7 — your own list, edited directly in settings.
                          &fAPI Words&r&7 — keywords other installed mods have registered (read-only list, shown
                            for visibility). Mute all of them at once with the &fEnable API Words&r&7 toggle.
                        Hover a highlighted message to see which keyword(s) matched. Click it to revert that one
                        message back to its original formatting."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lThemes & Appearance&r
                        &7Settings → &fAppearance&r&7.
                          &fChat Theme&r&7 — color palette for tabs, scrollbar, and message highlights. Built-in:
                            Vanilla, Simple Dark, Bold, Pink, Mint, Lavender. Other mods can register their own.
                          &fDefault Panel BG Color&r&7 — message background for any panel that hasn't set its own.
                          &fDraggable Scrollbar&r&7 — click and drag the scrollbar on the right edge to scroll;
                            it glows when your mouse is near it if &fScrollbar Hover Glow&r&7 is on.
                          &fAlways Focused&r&7 (Panel Edit) — that panel stays at full opacity/line-count
                            permanently, instead of only briefly after a new message arrives.
                          &fPop Out&r&7 — right-click a tab → &fPop Out&r&7 turns it into its own floating panel,
                            then drops you into the layout editor to position it. Right-click it → &fClose Popout&r&7
                            to remove it again. Not available for Vanilla/Info/Search/Notes tabs."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lChat Heads&r
                        &7Works with the Chat Heads mod automatically if it's installed — nothing to configure.
                          &fBefore Name&r&7 (their default) — the head is part of the message text already,
                            MultiChat's normal rendering draws it with no special handling.
                          &fBefore Line&r&7 — MultiChat draws and positions the head itself. Your &fmain panel&r&7
                            grows slightly on its right edge to keep it selectable (shown as an amber strip in
                            &fPanel Layout&r&7 while active); other panels wrap around it instead of overflowing.
                        &7Switching Chat Heads' render mode mid-session is picked up automatically — every
                        panel reflows within a tick."""
        ).mc()).build());

        help.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lFormat Codes&r
                        &7Used in Timestamp Format and Collapse Counter Format fields.
                          &f&#RRGGBB&r&7 — hex color   \
                        &aa&r&7 — green   &bb&r&7 — aqua   &cc&r&7 — red   &dd&r&7 — purple
                          &ee&r&7 — yellow   &ff&r&7 — white   &77 — gray   &88 — dark gray
                          &l&fBold&r&7   &o&fItalic&r&7   &n&fUnderline&r&7   &m&fStrike&r&7   \
                        &fr&7 — reset   k — obfuscate"""
        ).mc()).build());
    }
}
