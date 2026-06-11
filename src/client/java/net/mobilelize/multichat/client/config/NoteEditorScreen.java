package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.NoteTab;

public class NoteEditorScreen extends ConfigScreen {

    private static final int LIST_TOP   = 32;
    private static final int ROW_H      = 20;
    private static final int PAD        = 4;

    private final NoteTab noteTab;
    private int scrollOffset = 0;

    public NoteEditorScreen(Screen parent, NoteTab noteTab) {
        super("Notes: " + noteTab.name(), parent);
        this.noteTab = noteTab;
    }

    private int listBottom() { return height - 40; }
    private int maxScroll()  {
        return Math.max(0, noteTab.noteEntries().size() * ROW_H - (listBottom() - LIST_TOP));
    }

    @Override
    protected void init() {
        scrollOffset = Math.min(scrollOffset, maxScroll());
        addConfigNav(width, height, NavPage.TABS);
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);

        LzDraw.drawCenteredText(ctx, LzText.wrapMc(title), width / 2, 10, 0xFFEE88);
        int count = noteTab.noteEntries().size();
        LzDraw.drawCenteredText(ctx, LzText.format("&7" + count + " saved note" + (count == 1 ? "" : "s")
            + "  ·  right-click messages to add"), width / 2, 21, 0xFFFFFF);

        int lx = width / 2 - 200;

        LzDraw.enableScissor(ctx, 0, LIST_TOP, width, listBottom());

        for (int i = 0; i < noteTab.noteEntries().size(); i++) {
            NoteTab.NoteEntry entry = noteTab.noteEntries().get(i);
            int y = LIST_TOP + i * ROW_H - scrollOffset;
            if (y + ROW_H <= LIST_TOP || y >= listBottom()) continue;

            int rowY1 = y + 1, rowY2 = y + ROW_H - 1;
            int textY = y + (ROW_H - 8) / 2;

            boolean hoverRow = mouseX >= lx && mouseX < lx + 400 && mouseY >= y && mouseY < y + ROW_H;
            LzDraw.fill(ctx, lx, rowY1, lx + 400, rowY2, hoverRow ? 0x44FFDD55 : 0x33DDBB33);
            LzDraw.drawBorder(ctx, lx, rowY1, lx + 400, rowY2, 0x66DDAA00);

            // Del button
            boolean hoverDel = mouseX >= lx + 362 && mouseX < lx + 398 && mouseY >= y && mouseY < y + ROW_H;
            LzDraw.fill(ctx, lx + 362, y + 2, lx + 398, y + ROW_H - 2,
                hoverDel ? 0xAAFF4444 : 0x66882222);
            LzDraw.drawBorder(ctx, lx + 362, y + 2, lx + 398, y + ROW_H - 2, 0x88FF5555);
            LzDraw.drawCenteredText(ctx, LzText.literal("Del"), lx + 380, textY, 0xFFFF8888);

            // Message text — truncate to fit available width
            LzDraw.drawText(ctx, entry.content(), lx + PAD, textY, 0xFFEEEEEE);
        }

        LzDraw.disableScissor(ctx);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = lx + 405;
            int trackH = listBottom() - LIST_TOP;
            int totalH = noteTab.noteEntries().size() * ROW_H;
            int thumbH = Math.max(20, trackH * trackH / totalH);
            int thumbY = LIST_TOP + (int)((long) scrollOffset * (trackH - thumbH) / max);
            LzDraw.fill(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x44FFFFFF);
            LzDraw.drawBorder(ctx, sbX, LIST_TOP, sbX + SB_W, listBottom(), 0x88666666);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH, 0xBBAAAAAA);
        }

        if (noteTab.noteEntries().isEmpty()) {
            LzDraw.drawCenteredText(ctx, LzText.format("&7No notes yet. Right-click a chat message → &eAdd to Notes&7."),
                width / 2, (LIST_TOP + listBottom()) / 2, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        int lx = width / 2 - 200;
        if (my < LIST_TOP || my >= listBottom()) return false;
        if (mx < lx || mx >= lx + 400) return false;

        int relY = (int)(my - LIST_TOP) + scrollOffset;
        int idx  = relY / ROW_H;
        if (idx < 0 || idx >= noteTab.noteEntries().size()) return false;

        // Del button
        if (mx >= lx + 362 && mx < lx + 398) {
            noteTab.removeNote(idx);
            MultichatClient.saveTabsToConfig();
            clearAndInit();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hDelta, double vDelta) {
        if (my >= LIST_TOP && my < listBottom()) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll(), scrollOffset - vDelta * ROW_H));
            return true;
        }
        return super.mouseScrolled(mx, my, hDelta, vDelta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
