package net.mobilelize.multichat.client;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzMutableText;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;

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
        return List.of(
            dim("─────────────────────────────────────────"),
            LzText.empty(),
            LzText.format("  &6&l✦ MultiChat"),
            LzText.empty(),
            plain("  This is Panel 2 — a second independent chat"),
            plain("  window. Each panel shows its own set of tabs."),
            LzText.empty(),
            LzText.empty()
                .append(plain("  "))
                .append(link("► Settings", "/multichat settings", "Open MultiChat settings."))
                .append(plain("   "))
                .append(link("► Panels", "/multichat panels", "Add, remove, or configure panels."))
                .append(plain("   "))
                .append(link("► Help", "/multichat help", "Open the full guide.")),
            LzText.empty(),
            dim("─────────────────────────────────────────"),
            LzText.empty(),
            header("  Navigation"),
            shortcut("  Ctrl+Arrow  ", "switch focused panel"),
            shortcut("  Arrow keys  ", "switch tabs"),
            shortcut("  Ctrl+F      ", "search chat"),
            shortcut("  Right-click ", "copy · search · delete message"),
            LzText.empty(),
            dim("─────────────────────────────────────────"),
            LzText.empty(),
            header("  Disable"),
            LzText.empty()
                .append(plain("  Hide this tab:  "))
                .append(link("[ Hide ]", "/multichat info hide", "Remove this tab from Panel 2. Re-add it from Panel settings.")),
            plain("  Disable mod: remove from your /mods folder,"),
            plain("  or use Mod Menu if installed."),
            LzText.empty(),
            dim("─────────────────────────────────────────")
        );
    }

    // ── Text helpers ──────────────────────────────────────────────────────────

    private static LzMutableText plain(String s) {
        return LzText.literal(s).withStyle(LzStyle.ofFormatting(LzFormatting.WHITE));
    }

    private static LzMutableText dim(String s) {
        return LzText.literal(s).withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY));
    }

    private static LzMutableText header(String s) {
        return LzText.format("&e&l" + s);
    }

    private static LzText shortcut(String key, String desc) {
        return LzText.empty()
            .append(dim(key))
            .append(plain("→ " + desc));
    }

    private static LzMutableText link(String label, String command, String tooltip) {
        LzText hoverText = LzText.literal(tooltip).withStyle(LzStyle.ofFormatting(LzFormatting.GRAY));
        LzMutableText t = LzText.literal(label);
        t.mc().styled(s -> s
            .withColor(Colors.TEXT_LINK)
            .withUnderline(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText.mc())));
        return t;
    }
}
