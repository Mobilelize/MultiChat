package net.mobilelize.multichat.client.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.mobilelize.fabriclib.client.LzButton;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzScreen;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.Keys;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.HashMap;
import java.util.Map;

public class PanelLayoutScreen extends LzScreen {

    private static final int GRID_SIZE      = 8;
    private static final int EDGE_GRAB      = 7;   // px from edge that counts as resize hit
    private static final int OUTER_PAD      = 4;   // px the outer outline extends left/right
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

    // anchor, customX, customY, customWidth, customHeightLines
    private final Map<ChatPanel, Object[]> originals = new HashMap<>();

    public PanelLayoutScreen(Screen parent) {
        super("Panel Layout");
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (originals.isEmpty()) {
            for (ChatPanel p : ChatRenderer.PANELS)
                originals.put(p, new Object[] { p.anchor, p.customX, p.customY, p.customWidth, p.customHeightLines });
        }

        addDrawableChild(LzButton.of("Save",   width / 2 - 53, height - 26, 50, 18, b -> save()));
        addDrawableChild(LzButton.of("Cancel", width / 2 + 3,  height - 26, 50, 18, b -> cancel()));
        addDrawableChild(LzButton.of(snapEnabled ? "Snap: ON" : "Snap: OFF", width - 82, height - 26, 78, 18, b -> {
            snapEnabled = !snapEnabled;
            b.setMessage(LzText.literal(snapEnabled ? "Snap: ON" : "Snap: OFF").mc());
        }));
        addDrawableChild(LzButton.of("To Wall", 4, height - 26, 58, 18, b -> snapToNearestWall(lastDragged)));
    }

    // Screen-pixel bounding box [x1, y1, x2, y2]
    private int[] bounds(ChatPanel panel) {
        float scale      = panel.resolveScale();
        int   chatWidth  = panel.resolveWidth();
        int   lines      = panel.resolveLines();
        int   lineHeight = panel.resolveLineHeight();
        int   xOffset    = panel.computeXOffset(width);
        int   topY       = panel.resolveTopY(height);

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
        if (panel == null || panel.primary) return;
        int[] b = bounds(panel);
        int cx = (b[0] + b[2]) / 2;
        panel.anchor = cx <= width / 2 ? ChatPanel.Anchor.LEFT : ChatPanel.Anchor.RIGHT;
    }

    private boolean isDirty() {
        for (Map.Entry<ChatPanel, Object[]> e : originals.entrySet()) {
            ChatPanel p = e.getKey();
            Object[]  o = e.getValue();
            if (p.anchor            != (ChatPanel.Anchor) o[0]) return true;
            if (p.customX           != (float) o[1])             return true;
            if (p.customY           != (float) o[2])             return true;
            if (p.customWidth       != (int) o[3])              return true;
            if (p.customHeightLines != (int) o[4])              return true;
        }
        return false;
    }

    // Convert panel from wall-anchor to CUSTOM at its current visual position (center stored as screen fractions).
    private void pinToCustom(ChatPanel panel, int[] b) {
        if (panel.anchor == ChatPanel.Anchor.CUSTOM) return;
        panel.customX = (b[0] + b[2]) / 2f / width;
        panel.customY = (b[1] + b[3]) / 2f / height;
        panel.anchor  = ChatPanel.Anchor.CUSTOM;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        LzDraw.fill(ctx, 0, 0, width, height, 0x55000000);

        if (snapEnabled) {
            for (int gx = 0; gx < width;  gx += GRID_SIZE)
                LzDraw.fill(ctx, gx, 0, gx + 1, height, 0x0DFFFFFF);
            for (int gy = 0; gy < height; gy += GRID_SIZE)
                LzDraw.fill(ctx, 0, gy, width, gy + 1, 0x0DFFFFFF);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;
            int[] b  = bounds(panel);
            int   x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];
            int   cx = (x1 + x2) / 2;
            int   cy = (y1 + y2) / 2;
            boolean hov = mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;

            // Outer border extends left/right to show true rendered panel extent
            int outerCol = panel.primary ? 0x55446699 : 0x55FF8800;
            LzDraw.drawBorder(ctx, x1 - OUTER_PAD, y1, x2 + OUTER_PAD, y2, outerCol);

            if (panel.primary) {
                LzDraw.fill(ctx, x1, y1, x2, y2, 0x1A4488FF);
                LzDraw.drawBorder(ctx, x1, y1, x2, y2, 0xFF4488FF);
                LzDraw.drawCenteredText(ctx, LzText.literal(panel.name + " (locked)"), cx, cy - 4, 0xFF88AAFF);
                LzDraw.drawCenteredText(ctx, LzText.literal("primary panel"),           cx, cy + 5, 0x884488FF);
            } else {
                boolean drag  = dragging == panel;
                boolean resiz = resizing == panel;
                int fillCol   = (drag || resiz) ? 0x33FFAA00 : (hov ? 0x22FFFFFF : 0x11FFFFFF);
                int borderCol = (drag || resiz) ? 0xFFFFAA00 : (hov ? 0xFFFFFFFF : 0xFF888888);
                int labelCol  = (drag || resiz) ? 0xFFFFCC44 : (hov ? 0xFFFFFFFF : 0xFFCCCCCC);

                LzDraw.fill(ctx, x1, y1, x2, y2, fillCol);
                LzDraw.drawBorder(ctx, x1, y1, x2, y2, borderCol);

                String name   = panel.name.isEmpty() ? String.valueOf(panel.id) : panel.name;
                String anchor = panel.anchor == ChatPanel.Anchor.CUSTOM
                    ? String.format("%.2f, %.2f", panel.customX, panel.customY)
                    : panel.anchor.name().toLowerCase() + (panel.margin != 0 ? " +" + panel.margin : "");

                LzDraw.drawCenteredText(ctx, LzText.literal(name),   cx, cy - 4, labelCol);
                LzDraw.drawCenteredText(ctx, LzText.literal(anchor), cx, cy + 5, 0x88FFFFFF);

                LzDraw.fill(ctx, x1 + 1, y1 + 1, x2 - 1, y1 + 10, drag ? 0x66FFAA00 : 0x44FFFFFF);
                LzDraw.drawCenteredText(ctx, LzText.literal("drag"), cx, y1 + 2, drag ? 0xFFFFCC44 : 0x88FFFFFF);

                boolean hovRight = !drag && mouseX >= x2 - EDGE_GRAB && mouseX <= x2 + EDGE_GRAB
                    && mouseY >= y1 && mouseY < y2;
                int rightCol = (resiz && resizeDir == ResizeDir.WIDTH) ? 0xCCFFAA00 : hovRight ? 0xAAFFFFFF : 0x44FFFFFF;
                LzDraw.fill(ctx, x2 - 3, y1 + 12, x2 + 3, y2 - 4, rightCol);

                boolean hovBot = !drag && mouseX >= x1 && mouseX < x2
                    && mouseY >= y2 - EDGE_GRAB && mouseY <= y2 + EDGE_GRAB;
                int botCol = (resiz && resizeDir == ResizeDir.HEIGHT) ? 0xCCFFAA00 : hovBot ? 0xAAFFFFFF : 0x44FFFFFF;
                LzDraw.fill(ctx, x1 + 12, y2 - 3, x2 - 12, y2 + 3, botCol);

                if (resiz) {
                    String sizeInfo = "w:" + panel.resolveWidth() + "  h:" + panel.resolveLines() + " lines";
                    LzDraw.drawCenteredText(ctx, LzText.literal(sizeInfo), cx, y1 - 10, 0xFFFFCC44);
                }
            }
        }

        LzDraw.drawCenteredText(ctx,
            LzText.literal("Drag panels  |  Drag edges to resize  |  Esc / Cancel = revert"), width / 2, 6, 0xFFAAAAAA);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        // Resize handles take priority over drag
        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (panel.primary || !panel.enabled) continue;
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

        // Drag
        ChatPanel hit = null;
        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (panel.primary || !panel.enabled) continue;
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
        hit.customX = initCX / (float) width;
        hit.customY = initCY / (float) height;
        dragging    = hit;
        lastDragged = hit;
        dragOffsetX = (int) mouseX - initCX;
        dragOffsetY = (int) mouseY - initCY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (resizing != null && button == 0) {
            float scale = resizing.resolveScale();

            if (resizeDir == ResizeDir.WIDTH) {
                // Left edge (x1) stays fixed; right edge follows mouse. Center shifts.
                resizing.customWidth = Math.max(40, (int)((mouseX - resizeAnchorX1) / scale));
                resizing.customX = (resizeAnchorX1 + resizing.customWidth * scale / 2f) / width;
            } else {
                // Top (y1) stays fixed; bottom follows mouse. Center shifts.
                int   lh         = resizing.resolveLineHeight();
                float y1Unscaled = resizeAnchorY1 / scale;
                float newTopY    = (float)(mouseY / scale) - ChatRenderer.TAB_HEIGHT;
                resizing.customHeightLines = Math.max(2, (int)((newTopY - y1Unscaled) / lh));
                resizing.customY = (resizeAnchorY1 + (resizing.customHeightLines * lh + ChatRenderer.TAB_HEIGHT) * scale / 2f) / height;
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

            dragging.customX = finalCX / (float) width;
            dragging.customY = snap(rawCY) / (float) height;
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        resizing = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == Keys.ESCAPE) { attemptClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void attemptClose() {
        if (isDirty()) LzClient.mc().setScreen(new ConfirmScreen(this, "Save changes?", this::save, this::restoreAndClose));
        else restoreAndClose();
    }

    private void save() {
        MultichatClient.saveTabsToConfig();
        close();
    }

    private void cancel() { attemptClose(); }

    private void restoreAndClose() {
        for (Map.Entry<ChatPanel, Object[]> e : originals.entrySet()) {
            ChatPanel p = e.getKey();
            Object[]  o = e.getValue();
            p.anchor            = (ChatPanel.Anchor) o[0];
            p.customX           = (float) o[1];
            p.customY           = (float) o[2];
            p.customWidth       = (int) o[3];
            p.customHeightLines = (int) o[4];
        }
        close();
    }

    @Override
    public void close() { LzClient.mc().setScreen(parent); }

    @Override
    public boolean shouldPause() { return false; }
}
