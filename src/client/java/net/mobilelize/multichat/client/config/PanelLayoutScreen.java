package net.mobilelize.multichat.client.config;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.world.LzWorld;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.HashMap;
import java.util.Map;

public class PanelLayoutScreen extends LzScreen {

    private static final int GRID_SIZE      = 8;
    private static final int EDGE_GRAB      = 7;   // px from edge that counts as resize hit
    private static final int OUTER_PAD      = 4;   // px the outer outline extends left
    private static final int OUTER_PAD_R   = 8;   // px the outer outline extends right
    private static final int WALL_SNAP_DIST = 24;  // px from wall-anchor center that triggers wall snap

    private enum ResizeDir { WIDTH, HEIGHT }

    private final Screen parent;
    private boolean snapEnabled = true;

    private ChatPanel dragging     = null;
    private ChatPanel lastDragged  = null;
    private int       dragOffsetX  = 0;
    private int       dragOffsetY  = 0;

    private ChatPanel  resizing       = null;
    private ResizeDir  resizeDir      = null;
    private int        resizeAnchorX1 = 0;
    private int        resizeAnchorY1 = 0;

    private ChatPanel unfocusedLineDragging = null;

    // anchor, customX, customY, customWidth, customHeightLines, customUnfocusedHeightLines
    private final Map<ChatPanel, Object[]> originals = new HashMap<>();

    public PanelLayoutScreen(Screen parent) {
        super("Panel Layout");
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (originals.isEmpty()) {
            for (ChatPanel p : ChatManager.getAllPanels())
                originals.put(p, new Object[] { p.anchor(), p.customX(), p.customY(), p.customWidth(), p.customHeightLines(), p.customUnfocusedHeightLines() });
        }

        addRenderableWidget(LzButton.of("Save",   width / 2 - 53, height - 26, 50, 18, b -> save()));
        addRenderableWidget(LzButton.of("Cancel", width / 2 + 3,  height - 26, 50, 18, b -> cancel()));
        addRenderableWidget(LzButton.of(snapEnabled ? "Snap: ON" : "Snap: OFF", width - 82, height - 26, 78, 18, b -> {
            snapEnabled = !snapEnabled;
            b.setMessage(LzText.literal(snapEnabled ? "Snap: ON" : "Snap: OFF"));
        }));
        addRenderableWidget(LzButton.of("To Wall", 4, height - 26, 58, 18, b -> snapToNearestWall(lastDragged)));
    }

    // Unfocused line count from MC options — used as fallback when customUnfocusedHeightLines is unset.
    private int unfocusedMcLines(ChatPanel panel) {
        double h = LzClient.mc().options.chatHeightUnfocused().get();
        int heightPx = (int)(h * 160.0 + 20.0);
        return Math.max(1, heightPx / panel.resolveLineHeight());
    }

    private int focusedLines(ChatPanel panel) {
        return panel.resolveFocusedLines();
    }

    // TopY using focused line count (for CUSTOM panels, topY depends on line count).
    private int focusedTopY(ChatPanel panel) {
        if (panel.primary() || panel.anchor() != ChatPanel.Anchor.CUSTOM)
            return LzMath.floor((float)(height - 40) / panel.resolveScale());
        int lines      = focusedLines(panel);
        int lineHeight = panel.resolveLineHeight();
        return (int)(panel.customY() * height / panel.resolveScale()
                   + lines * lineHeight / 2f
                   - ChatRenderer.TAB_HEIGHT / 2f);
    }

    // Screen-pixel bounding box [x1, y1, x2, y2]
    private int[] bounds(ChatPanel panel) {
        float scale      = panel.resolveScale();
        int   chatWidth  = panel.resolveWidth();
        int   lines      = focusedLines(panel);
        int   lineHeight = panel.resolveLineHeight();
        int   xOffset    = panel.computeXOffset(width);
        int   topY       = focusedTopY(panel);

        int x1 = Math.round((4 + xOffset) * scale);
        int y1 = Math.round((topY - lines * lineHeight) * scale);
        int x2 = Math.round((4 + xOffset + chatWidth) * scale);
        int y2 = Math.round((topY + ChatRenderer.TAB_HEIGHT) * scale);
        return new int[] { x1, y1, x2, y2 };
    }

    private int snap(int v) {
        return snapEnabled ? Math.round(v / (float) GRID_SIZE) * GRID_SIZE : v;
    }

    // Docks the panel to LEFT or RIGHT anchor based on which side its center is closer to.
    private void snapToNearestWall(ChatPanel panel) {
        if (panel == null || panel.primary()) return;
        int[] b = bounds(panel);
        int cx = (b[0] + b[2]) / 2;
        panel.setAnchor(cx <= width / 2 ? ChatPanel.Anchor.LEFT : ChatPanel.Anchor.RIGHT);
    }

    private boolean isDirty() {
        for (Map.Entry<ChatPanel, Object[]> e : originals.entrySet()) {
            ChatPanel p = e.getKey();
            Object[]  o = e.getValue();
            if (p.anchor()            != o[0]) return true;
            if (p.customX()           != (float) o[1])            return true;
            if (p.customY()           != (float) o[2])            return true;
            if (p.customWidth()       != (int) o[3])              return true;
            if (p.customHeightLines()          != (int) o[4]) return true;
            if (p.customUnfocusedHeightLines() != (int) o[5]) return true;
        }
        return false;
    }

    private int unfocusedLineScreenY(ChatPanel panel) {
        float scale      = panel.resolveScale();
        int   lineHeight = panel.resolveLineHeight();
        int   topY       = focusedTopY(panel);
        int   ufLines    = panel.customUnfocusedHeightLines() > 0
            ? panel.customUnfocusedHeightLines()
            : unfocusedMcLines(panel);
        ufLines = Math.min(ufLines, focusedLines(panel)); // clamp: can't exceed focused height
        return Math.round((topY - ufLines * lineHeight) * scale);
    }

    // Convert panel from wall-anchor to CUSTOM at its current visual position (center stored as screen fractions).
    private void pinToCustom(ChatPanel panel, int[] b) {
        if (panel.anchor() == ChatPanel.Anchor.CUSTOM) return;
        panel.setCustomX((b[0] + b[2]) / 2f / width);
        panel.setCustomY((b[1] + b[3]) / 2f / height);
        panel.setAnchor(ChatPanel.Anchor.CUSTOM);
    }

    @Override
    public void lzRender(LzDrawContext ctx, int mouseX, int mouseY, float delta) {
        if (LzWorld.isLoaded()) LzDraw.fill(ctx, 0, 0, width, height, Colors.OVERLAY);
        super.lzRender(ctx, mouseX, mouseY, delta);

        if (snapEnabled) {
            for (int gx = 0; gx < width;  gx += GRID_SIZE)
                LzDraw.fill(ctx, gx, 0, gx + 1, height, Colors.GRID_LINE);
            for (int gy = 0; gy < height; gy += GRID_SIZE)
                LzDraw.fill(ctx, 0, gy, width, gy + 1, Colors.GRID_LINE);
        }

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            int[] b  = bounds(panel);
            int   x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];
            int   cx = (x1 + x2) / 2;
            int   cy = (y1 + y2) / 2;
            boolean hov = mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;

            // Outer border extends left/right to show true rendered panel extent
            int outerCol = panel.primary() ? Colors.PANEL_PRIMARY_TINT : Colors.PANEL_SECONDARY_TINT;
            LzDraw.drawBorder(ctx, x1 - OUTER_PAD, y1, x2 + OUTER_PAD_R, y2, outerCol);

            if (panel.primary()) {
                LzDraw.fill(ctx, x1, y1, x2, y2, Colors.PANEL_LOCKED_FILL);
                LzDraw.drawBorder(ctx, x1, y1, x2, y2, Colors.PANEL_LOCKED_BORDER);
                LzDraw.drawCenteredText(ctx, LzText.literal(panel.name() + " (locked)"), cx, cy - 4, Colors.PANEL_LOCKED_TEXT);
                LzDraw.drawCenteredText(ctx, LzText.literal("primary panel"),            cx, cy + 5, Colors.PANEL_LOCKED_SUBTITLE);
            } else {
                boolean drag  = dragging == panel;
                boolean resiz = resizing == panel;
                int fillCol   = (drag || resiz) ? Colors.PANEL_DRAG_FILL   : (hov ? Colors.ROW_HOVER   : Colors.PANEL_FILL_FAINT);
                int borderCol = (drag || resiz) ? Colors.PANEL_DRAG_BORDER : (hov ? Colors.TEXT_WHITE   : Colors.TEXT_GRAY);
                int labelCol  = (drag || resiz) ? Colors.GOLD              : (hov ? Colors.TEXT_WHITE   : Colors.PANEL_NORMAL_LABEL);

                LzDraw.fill(ctx, x1, y1, x2, y2, fillCol);
                LzDraw.drawBorder(ctx, x1, y1, x2, y2, borderCol);

                String name   = panel.name().isEmpty() ? String.valueOf(panel.id()) : panel.name();
                String anchor = panel.anchor() == ChatPanel.Anchor.CUSTOM
                    ? String.format("%.2f, %.2f", panel.customX(), panel.customY())
                    : panel.anchor().name().toLowerCase() + (panel.margin() != 0 ? " +" + panel.margin() : "");

                LzDraw.drawCenteredText(ctx, LzText.literal(name),   cx, cy - 4, labelCol);
                LzDraw.drawCenteredText(ctx, LzText.literal(anchor), cx, cy + 5, Colors.PANEL_ANCHOR_TEXT);

                LzDraw.fill(ctx, x1 + 1, y1 + 1, x2 - 1, y1 + 10, drag ? Colors.PANEL_DRAG_HEADER : Colors.ROW_HOVER_STRONG);
                LzDraw.drawCenteredText(ctx, LzText.literal("drag"), cx, y1 + 2, drag ? Colors.GOLD : Colors.PANEL_ANCHOR_TEXT);

                boolean hovRight = !drag && mouseX >= x2 - EDGE_GRAB && mouseX <= x2 + EDGE_GRAB
                    && mouseY >= y1 && mouseY < y2;
                int rightCol = (resiz && resizeDir == ResizeDir.WIDTH) ? Colors.PANEL_RESIZE_ACTIVE : hovRight ? Colors.PANEL_RESIZE_HOVER : Colors.ROW_HOVER_STRONG;
                LzDraw.fill(ctx, x2 - 3, y1 + 12, x2 + 3, y2 - 4, rightCol);

                boolean hovBot = !drag && mouseX >= x1 && mouseX < x2
                    && mouseY >= y2 - EDGE_GRAB && mouseY <= y2 + EDGE_GRAB;
                int botCol = (resiz && resizeDir == ResizeDir.HEIGHT) ? Colors.PANEL_RESIZE_ACTIVE : hovBot ? Colors.PANEL_RESIZE_HOVER : Colors.ROW_HOVER_STRONG;
                LzDraw.fill(ctx, x1 + 12, y2 - 3, x2 - 12, y2 + 3, botCol);

                if (resiz) {
                    String sizeInfo = "w:" + panel.resolveWidth() + "  h:" + focusedLines(panel) + " lines";
                    LzDraw.drawCenteredText(ctx, LzText.literal(sizeInfo), cx, y1 - 10, Colors.GOLD);
                }

                if (!panel.lockUnfocusedToFocused()) {
                    int lineY  = unfocusedLineScreenY(panel);
                    boolean hovLine = unfocusedLineDragging == panel
                        || (dragging == null && resizing == null && unfocusedLineDragging == null
                            && mouseX >= x1 - OUTER_PAD && mouseX <= x2 + OUTER_PAD_R
                            && Math.abs(mouseY - lineY) <= EDGE_GRAB);
                    int lineCol = hovLine ? Colors.PANEL_UNFOCUSED_LINE : Colors.PANEL_UNFOCUSED_LINE_DIM;
                    LzDraw.fill(ctx, x1 - OUTER_PAD, lineY - 1, x2 + OUTER_PAD_R, lineY + 1, lineCol);
                    int ufLines = Math.min(
                        panel.customUnfocusedHeightLines() > 0 ? panel.customUnfocusedHeightLines() : unfocusedMcLines(panel),
                        focusedLines(panel));
                    LzDraw.drawText(ctx, LzText.literal("unfocused: " + ufLines + " lines"), x1, lineY + 3, hovLine ? Colors.PANEL_UNFOCUSED_LINE : Colors.PANEL_UNFOCUSED_LINE_DIM);
                }
            }
        }

        LzDraw.drawCenteredText(ctx,
            LzText.literal("Drag panels  |  Drag edges to resize  |  Green line = unfocused height  |  Esc / Cancel = revert"), width / 2, 6, Colors.TEXT_DIMMED);

    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean isDoubleClick) {
        double mouseX = click.x(); double mouseY = click.y(); int button = click.button();
        if (super.mouseClicked(click, isDoubleClick)) return true;
        if (button != 0) return false;

        // Resize handles take priority over drag
        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (panel.primary() || !panel.isEnabled()) continue;
            int[] b = bounds(panel);
            int x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];

            if (mouseX >= x2 - EDGE_GRAB && mouseX <= x2 + EDGE_GRAB && mouseY >= y1 && mouseY < y2) {
                pinToCustom(panel, b);
                resizing       = panel;
                resizeDir      = ResizeDir.WIDTH;
                resizeAnchorX1 = bounds(panel)[0];
                return true;
            }

            if (mouseX >= x1 && mouseX < x2 && mouseY >= y2 - EDGE_GRAB && mouseY <= y2 + EDGE_GRAB) {
                pinToCustom(panel, b);
                resizing       = panel;
                resizeDir      = ResizeDir.HEIGHT;
                resizeAnchorY1 = bounds(panel)[1];
                return true;
            }
        }

        // Unfocused height line drag
        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (panel.primary() || !panel.isEnabled() || panel.lockUnfocusedToFocused()) continue;
            int[] b = bounds(panel);
            int lineY = unfocusedLineScreenY(panel);
            if (mouseX >= b[0] - OUTER_PAD && mouseX <= b[2] + OUTER_PAD_R
                    && Math.abs(mouseY - lineY) <= EDGE_GRAB) {
                unfocusedLineDragging = panel;
                return true;
            }
        }

        // Drag
        ChatPanel hit = null;
        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (panel.primary() || !panel.isEnabled()) continue;
            int[] b = bounds(panel);
            if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) hit = panel;
        }
        if (hit == null) return false;

        int[] b = bounds(hit);
        pinToCustom(hit, b);

        // Snap initial center to grid so first drag move doesn't jump
        int[] nb    = bounds(hit);
        int initCX  = snap((nb[0] + nb[2]) / 2);
        int initCY  = snap((nb[1] + nb[3]) / 2);
        hit.setCustomX(initCX / (float) width);
        hit.setCustomY(initCY / (float) height);
        dragging    = hit;
        lastDragged = hit;
        dragOffsetX = (int) mouseX - initCX;
        dragOffsetY = (int) mouseY - initCY;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        double mouseX = click.x(); double mouseY = click.y(); int button = click.button();
        if (unfocusedLineDragging != null && button == 0) {
            float scale      = unfocusedLineDragging.resolveScale();
            int   lineHeight = unfocusedLineDragging.resolveLineHeight();
            int   topY       = focusedTopY(unfocusedLineDragging);
            int   maxLines   = focusedLines(unfocusedLineDragging);
            int   newLines   = (int)((topY - mouseY / scale) / lineHeight);
            newLines = Math.max(1, Math.min(newLines, maxLines));
            unfocusedLineDragging.setCustomUnfocusedHeightLines(newLines);
            return true;
        }

        if (resizing != null && button == 0) {
            float scale = resizing.resolveScale();

            if (resizeDir == ResizeDir.WIDTH) {
                // Left edge (x1) stays fixed; right edge follows mouse. Center shifts.
                resizing.setCustomWidth(Math.max(40, (int)((mouseX - resizeAnchorX1) / scale)));
                resizing.setCustomX((resizeAnchorX1 + resizing.customWidth() * scale / 2f) / width);
            } else {
                // Top (y1) stays fixed; bottom follows mouse. Center shifts.
                int   lh         = resizing.resolveLineHeight();
                float y1Unscaled = resizeAnchorY1 / scale;
                float newTopY    = (float)(mouseY / scale) - ChatRenderer.TAB_HEIGHT;
                resizing.setCustomHeightLines(Math.max(2, (int)((newTopY - y1Unscaled) / lh)));
                resizing.setCustomY((resizeAnchorY1 + (resizing.customHeightLines() * lh + ChatRenderer.TAB_HEIGHT) * scale / 2f) / height);
            }
            return true;
        }

        if (dragging != null && button == 0) {
            float scale     = dragging.resolveScale();
            int   chatWidth = dragging.resolveWidth();
            int   rawCX     = (int) mouseX - dragOffsetX;
            int   rawCY     = (int) mouseY - dragOffsetY;

            int finalCX;
            if (snapEnabled) {
                // Exact center-X for LEFT and RIGHT anchor positions
                int leftCX  = Math.round((4 + chatWidth / 2f) * scale);
                int rightCX = Math.round(width - (8 + chatWidth / 2f) * scale);
                if      (Math.abs(rawCX - leftCX)  <= WALL_SNAP_DIST) finalCX = leftCX;
                else if (Math.abs(rawCX - rightCX) <= WALL_SNAP_DIST) finalCX = rightCX;
                else                                                   finalCX = snap(rawCX);
            } else {
                finalCX = rawCX;
            }

            dragging.setCustomX(finalCX / (float) width);
            dragging.setCustomY(snap(rawCY) / (float) height);
            return true;
        }

        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging              = null;
        resizing              = null;
        unfocusedLineDragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == Keys.ESCAPE) { attemptClose(); return true; }
        return super.keyPressed(input);
    }

    private void attemptClose() {
        if (isDirty()) LzClient.mc().gui.setScreen(new ConfirmScreen(this, "Save changes?", this::save, this::restoreAndClose));
        else restoreAndClose();
    }

    private void save() {
        MultichatClient.saveTabsToConfig();
        onClose();
    }

    private void cancel() { attemptClose(); }

    private void restoreAndClose() {
        for (Map.Entry<ChatPanel, Object[]> e : originals.entrySet()) {
            ChatPanel p = e.getKey();
            Object[]  o = e.getValue();
            p.setAnchor((ChatPanel.Anchor) o[0]);
            p.setCustomX((float) o[1]);
            p.setCustomY((float) o[2]);
            p.setCustomWidth((int) o[3]);
            p.setCustomHeightLines((int) o[4]);
            p.setCustomUnfocusedHeightLines((int) o[5]);
        }
        onClose();
    }

    @Override
    public void onClose() { LzClient.mc().gui.setScreen(parent); }
}
