package net.mobilelize.multichat.client.quickaction;

import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.client.LzTextField;
import net.mobilelize.fabriclib.network.LzConnection;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzFormatting;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.Colors;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.config.MultiChatConfig;

import java.util.List;

public class QuickActionsOverlay extends LzScreen {

    private static final int W          = 360;
    private static final int PAD        = 6;
    private static final int HEADER_H   = 52;
    private static final int ROW_H      = 14;
    private static final int LIST_PAD   = 4;
    private static final int CMD_SEC_H  = 48;
    private static final int SB_W       = 4;

    private final Screen parent;
    private String selText;
    private int    selectedIdx  = -1;
    private int    scrollOffset = 0;

    private LzTextField selectionField;
    private LzTextField commandField;

    private int ox, oy, oh;

    public QuickActionsOverlay(Screen parent, String selectedText) {
        super("Quick Actions");
        this.parent  = parent;
        this.selText = selectedText;
    }

    // ── layout helpers ────────────────────────────────────────────────────────

    private List<MultiChatConfig.QuickActionConfig> actions() { return MultichatClient.CONFIG.quickActions; }
    private int overlayX()      { return (width - W) / 2; }
    private int overlayY()      { return Math.max(10, (height - totalH()) / 2); }
    private int listStartY()    { return overlayY() + HEADER_H; }

    private int cmdH()          { return selectedIdx >= 0 ? CMD_SEC_H : 0; }
    private int maxListH()      { return Math.max(ROW_H, height * 3 / 4 - HEADER_H - LIST_PAD - cmdH()); }
    private int cappedListH()   { return actions().isEmpty() ? ROW_H : Math.min(actions().size() * ROW_H, maxListH()); }
    private int visibleRows()   { return Math.max(1, cappedListH() / ROW_H); }
    private int maxScroll()     { return Math.max(0, actions().size() - visibleRows()); }

    private int listEndY()      { return listStartY() + cappedListH() + LIST_PAD; }
    private int cmdFieldY()     { return listEndY() + 13; }
    private int totalH()        { return HEADER_H + cappedListH() + LIST_PAD + cmdH(); }
    private int selFieldY()     { return overlayY() + HEADER_H - PAD - 18; }

    // ── internal ──────────────────────────────────────────────────────────────

    private void reinit() {
        if (selectionField != null) selText = selectionField.getText();
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
        clearChildren();
        init();
    }

    private void scrollToSelected() {
        if (selectedIdx < 0) return;
        if (selectedIdx < scrollOffset)
            scrollOffset = selectedIdx;
        else if (selectedIdx >= scrollOffset + visibleRows())
            scrollOffset = selectedIdx - visibleRows() + 1;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    // ── screen lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
        int x = overlayX();

        selectionField = new LzTextField(x + PAD, selFieldY(), W - PAD * 2, 18);
        selectionField.setPlaceholder(LzText.literal("(no selection)").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
        selectionField.setText(selText);
        addSelectableChild(selectionField);

        if (selectedIdx >= 0 && selectedIdx < actions().size()) {
            String resolved = actions().get(selectedIdx).template.replace("{selected}", selText);
            commandField = new LzTextField(x + PAD, cmdFieldY(), W - PAD * 2, 18);
            commandField.setPlaceholder(LzText.literal("Command...").withStyle(LzStyle.ofFormatting(LzFormatting.DARK_GRAY)).mc());
            commandField.setText(resolved);
            addSelectableChild(commandField);
            setFocused(commandField);
        } else {
            commandField = null;
            setFocused(selectionField);
        }
    }

    @Override
    public void lzRenderBackground(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        super.lzRender(ctx, mouseX, mouseY, delta);

        int x = overlayX(), y = overlayY(), h = totalH();
        ox = x; oy = y; oh = h;

        LzDraw.fill(ctx, x, y, x + W, y + h, Colors.PANEL_BG);
        LzDraw.drawBorder(ctx, x, y, x + W, y + h, Colors.BORDER);

        LzDraw.drawText(ctx, LzText.literal("Quick Actions"), x + PAD, y + PAD, Colors.GOLD);
        LzDraw.drawText(ctx, LzText.format("&7Selected text &8(editable)&7:"), x + PAD, y + PAD + 12, Colors.TEXT_WHITE);
        selectionField.render(ctx.mc(), mouseX, mouseY, delta);

        LzDraw.fill(ctx, x + 1, y + HEADER_H - 1, x + W - 1, y + HEADER_H, Colors.PANEL_BG);

        List<MultiChatConfig.QuickActionConfig> list = actions();
        int listStart = listStartY();
        int listClipEnd = listStart + cappedListH();

        LzDraw.enableScissor(ctx, x, listStart, x + W, listClipEnd);
        if (list.isEmpty()) {
            LzDraw.drawText(ctx, LzText.format("&7No actions. Add some in Settings → Quick Actions."),
                x + PAD, listStart + 3, Colors.TEXT_WHITE);
        } else {
            for (int i = scrollOffset; i < list.size() && i < scrollOffset + visibleRows() + 1; i++) {
                var a  = list.get(i);
                int ry = listStart + (i - scrollOffset) * ROW_H;
                boolean sel = selectedIdx == i;
                boolean hov = !sel && mouseX >= x && mouseX < x + W - SB_W - 1
                    && mouseY >= ry && mouseY < ry + ROW_H;

                if (sel)      LzDraw.fill(ctx, x + 1, ry, x + W - SB_W - 2, ry + ROW_H, Colors.ROW_SELECTED_GOLD);
                else if (hov) LzDraw.fill(ctx, x + 1, ry, x + W - SB_W - 2, ry + ROW_H, Colors.ROW_HOVER_STRONG);

                LzDraw.drawText(ctx, LzText.literal(a.name), x + PAD, ry + 3,
                    sel ? Colors.GOLD : (hov ? Colors.TEXT_WHITE : Colors.TEXT_SCROLLBAR_DRAG));
                String tpl = a.template.length() > 30 ? a.template.substring(0, 28) + "..." : a.template;
                LzDraw.drawText(ctx, LzText.literal(tpl), x + W / 2, ry + 3, sel ? Colors.TEXT_GOLD_DIM : Colors.TEXT_GRAY);
            }
        }
        LzDraw.disableScissor(ctx);

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int sbX    = x + W - SB_W - 1;
            int trackH = cappedListH();
            int thumbH = Math.max(8, trackH * visibleRows() / list.size());
            int thumbY = listStart + scrollOffset * (trackH - thumbH) / max;
            LzDraw.fill(ctx, sbX, listStart, sbX + SB_W, listStart + trackH, Colors.SCROLLBAR_TRACK_DIM);
            LzDraw.fill(ctx, sbX + 1, thumbY, sbX + SB_W - 1, thumbY + thumbH, Colors.SCROLLBAR_THUMB);
        }

        // Command section
        if (selectedIdx >= 0 && commandField != null) {
            int divY = listEndY() - 1;
            LzDraw.fill(ctx, x + 1, divY, x + W - 1, divY + 1, Colors.BORDER_DARK);
            LzDraw.drawText(ctx, LzText.format("&7Command &8(editable)&7:"), x + PAD, listEndY() + 3, Colors.TEXT_WHITE);
            commandField.render(ctx.mc(), mouseX, mouseY, delta);
            LzDraw.drawCenteredText(ctx, LzText.format("&7Enter to run  ·  Esc to cancel"),
                x + W / 2, cmdFieldY() + 22, Colors.TEXT_WHITE);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (mouseX < ox || mouseX > ox + W || mouseY < oy || mouseY > oy + oh) {
            close(); return true;
        }

        if (button == 0) {
            List<MultiChatConfig.QuickActionConfig> list = actions();
            int listStart = listStartY();
            int listClipEnd = listStart + cappedListH();

            if (mouseY >= listStart && mouseY < listClipEnd && !list.isEmpty()) {
                int idx = scrollOffset + ((int) mouseY - listStart) / ROW_H;
                if (idx >= 0 && idx < list.size()) {
                    if (selectedIdx == idx) {
                        runCurrent();
                    } else {
                        selectedIdx = idx;
                        scrollToSelected();
                        reinit();
                    }
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) vDelta, maxScroll()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ENTER || keyCode == Keys.NUMPAD_ENTER) {
            if (selectedIdx >= 0) { runCurrent(); return true; }
        }
        if (keyCode == Keys.ESCAPE) {
            if (selectedIdx >= 0) { selectedIdx = -1; reinit(); return true; }
            close(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void runCurrent() {
        if (commandField == null) return;
        String cmd = commandField.getText().trim();
        if (cmd.isEmpty()) { close(); return; }
        if (MultichatClient.CONFIG.quickActionPasteToChat) {
            MultichatClient.pendingChatText = cmd;
        } else if (cmd.startsWith("/")) {
            LzConnection.sendChatCommand(cmd.substring(1));
        } else {
            LzConnection.sendChatMessage(cmd);
        }
        close();
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
