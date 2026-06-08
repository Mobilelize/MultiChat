package net.mobilelize.multichat.client.render;

import net.mobilelize.fabriclib.render.LzDrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzChatVisibility;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.client.LzOptions;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatSelection;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.SearchTab;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.ping.PingPlayer;
import net.mobilelize.multichat.mixin.client.ChatHudAccessor;

import net.minecraft.text.Style;
import net.minecraft.client.gui.hud.MessageIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatRenderer {

    public static final List<ChatPanel> PANELS = new ArrayList<>();

    // Convenience references — same objects as PANELS.get(0) and PANELS.get(1)
    public static final ChatPanel LEFT;
    public static final ChatPanel RIGHT;

    static {
        LEFT = new ChatPanel();
        LEFT.setPrimary(true);
        LEFT.setEnabled(true);
        LEFT.setId(0);
        LEFT.setName("Main");
        LEFT.setLockUnfocusedToFocused(false);
        PANELS.add(LEFT);

        RIGHT = new ChatPanel();
        RIGHT.setAnchor(ChatPanel.Anchor.RIGHT);
        RIGHT.setEnabled(true);
        RIGHT.setId(1);
        RIGHT.setName("Second Panel");
        RIGHT.setLockUnfocusedToFocused(false);
        PANELS.add(RIGHT);
    }

    public static ChatPanel primaryPanel() { return PANELS.getFirst(); }

    public static ChatTab   contextTab    = null;
    public static ChatPanel focusedPanel  = LEFT;

    public static int             currentTick        = 0;
    public static Set<LzChatEntry> highlightEntries   = new HashSet<>();
    public static int              highlightUntilTick = 0;
    public static Set<LzChatEntry> copyHighlightEntries = new HashSet<>();
    public static int              copyHighlightUntil = 0;

    private static long wallTimeStart = -1;
    private static int  tickStart     = 0;

    public static final List<ChatTab> TABS = new ArrayList<>(List.of(
        new ChatTab("All",     TabType.ALL,     List.of(0, 1)),
        new ChatTab("Vanilla", TabType.VANILLA, List.of(0))
    ));

    public static int lastMouseX = 0;

    public static final int TAB_HEIGHT    = 10;
    public static final int ARROW_W      = 10;
    private static final int FLASH_TICKS = 12;

    public static int getTabBarY(ChatPanel panel, int topY, int visibleLineCount, int lineHeight) {
        return panel.tabsAtTop() ? topY - visibleLineCount * lineHeight - TAB_HEIGHT : topY;
    }

    public static String getTabLabel(ChatPanel panel, ChatTab tab) {
        if (panel.showBadges() && tab.showBadge() && tab.hasUnread())
            return tab.name() + " [" + tab.unreadCount() + "]";
        return tab.name();
    }

    public static void resetForRefresh() {
        PingPlayer.suppressPingsUntil = System.currentTimeMillis() + 10_000L;
        PANELS.forEach(ChatPanel::resetScrollPositions);
        TABS.forEach(t -> {
            t.clearMessages();
            t.clearUnread();
        });
    }

    public static void render(ChatHud chat, LzDrawContext context, int currentTick,
                               int mouseX, int mouseY, boolean focused, boolean skipLeftMessages) {
        ChatHudAccessor accessor = (ChatHudAccessor) chat;

        lastMouseX = mouseX;
        ChatRenderer.currentTick = currentTick;
        if (wallTimeStart < 0) { wallTimeStart = System.currentTimeMillis(); tickStart = currentTick; }

        if (LzOptions.chatVisibility() == LzChatVisibility.HIDDEN) return;

        boolean anyVisible = PANELS.stream().anyMatch(p -> p.isVisibleInChat(focused));
        if (!anyVisible) return;

        LzClient.mc().getProfiler().push("multichat");

        int windowHeight   = LzDraw.scaledHeight(context);
        int windowWidth    = LzDraw.scaledWidth(context);
        double lineSpacing = LzOptions.chatLineSpacing();
        boolean hasUnread  = accessor.getHasUnreadNewMessages();

        for (ChatPanel panel : PANELS) {
            if (!panel.isVisibleInChat(focused)) continue;

            ChatTab activeTab = panel.getActiveTab();
            if (activeTab == null) continue;

            float scale          = panel.resolveScale();
            int chatWidth        = panel.resolveWidth();
            double chatOpacity   = panel.resolveChatOpacity();
            double bgOpacity     = panel.resolveBgOpacity();
            int lineHeight       = panel.resolveLineHeight();
            int textOffset       = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
            int visibleLineCount = panel.resolveLines(focused);
            int topY             = panel.resolveTopY(windowHeight);
            int xOffset          = panel.computeXOffset(windowWidth);

            int tabY = getTabBarY(panel, topY, visibleLineCount, lineHeight);
            if (focused && panel.showTabs()) renderTabBar(context, panel, xOffset, tabY, scale, chatWidth);

            if (activeTab.type() == TabType.VANILLA) continue;
            if (panel == LEFT && skipLeftMessages) continue;

            List<LzChatLine> msgs = getMessages(panel, activeTab);
            if (msgs.isEmpty()) continue;

            int hoveredIndex = computeHoveredIndex(panel, mouseX, mouseY,
                    visibleLineCount, windowHeight, scale, lineHeight, chatWidth, xOffset, focused);
            updateCursor(context, panel, hoveredIndex);

            int lineBgRgb = (activeTab.msgBgColor() != 0 ? activeTab.msgBgColor() : panel.bgColor()) & 0xFFFFFF;
            renderAt(context, currentTick, focused,
                    msgs, msgs.size(), panel.getScrolledLines(activeTab), hasUnread,
                    chatWidth, topY, scale, chatOpacity, bgOpacity,
                    lineHeight, textOffset, visibleLineCount, hoveredIndex, xOffset, panel, lineBgRgb);
        }

        LzClient.mc().getProfiler().pop();
    }

    private static void renderTabBar(LzDrawContext context,
                                      ChatPanel panel, int xOffset, int topY, float scale, int chatWidth) {
        LzDraw.pushMatrix(context);
        LzDraw.scale(context, scale, scale);
        LzDraw.translate(context, 4.0F + xOffset, 0.0F);

        List<ChatTab> visibleTabs = panel.getVisibleTabs();
        panel.setTabScrollOffset(LzMath.clamp(panel.tabScrollOffset(), 0, Math.max(0, visibleTabs.size() - 1)));

        boolean hasLeft = panel.tabScrollOffset() > 0;

        int simX = hasLeft ? -4 + ARROW_W : -4;
        int simEnd = chatWidth + 4 - ARROW_W;
        boolean hasRight = false;
        for (int i = panel.tabScrollOffset(); i < visibleTabs.size(); i++) {
            int tw = LzDraw.textWidth(getTabLabel(panel, visibleTabs.get(i))) + 8;
            if (simX + tw > simEnd) { hasRight = true; break; }
            simX += tw + 1;
        }

        int tabAreaEnd = chatWidth + 4 - (hasRight ? ARROW_W : 0);

        int x = -4;
        if (hasLeft) {
            LzDraw.fill(context, x, topY, x + ARROW_W, topY + TAB_HEIGHT, 0x88444444);
            LzDraw.drawText(context, "<", x + 2, topY + 1, 0xFFCCCCCC);
            x += ARROW_W;
        }

        int lastRendered = panel.tabScrollOffset() - 1;
        for (int i = panel.tabScrollOffset(); i < visibleTabs.size(); i++) {
            ChatTab tab = visibleTabs.get(i);
            String label = getTabLabel(panel, tab);
            int tabWidth = LzDraw.textWidth(label) + 8;
            if (x + tabWidth > tabAreaEnd) break;

            boolean active = (i == panel.activeTabIndex());
            LzDraw.fill(context, x, topY, x + tabWidth, topY + TAB_HEIGHT, tabBgColor(tab, active, panel));
            LzDraw.drawText(context, label, x + 4, topY + 1, tabFgColor(tab, active, panel));
            x += tabWidth + 1;
            lastRendered = i;
        }
        panel.setTabLastRendered(lastRendered);

        if (hasRight && lastRendered + 1 < visibleTabs.size()) {
            int remaining = tabAreaEnd - x;
            if (remaining > 4) {
                ChatTab nextTab = visibleTabs.get(lastRendered + 1);
                String label = getTabLabel(panel, nextTab);
                int tabWidth = LzDraw.textWidth(label) + 8;
                boolean active = (lastRendered + 1 == panel.activeTabIndex());

                int sx1 = Math.round((x + 4 + xOffset) * scale);
                int sy1 = Math.round(topY * scale);
                int sx2 = Math.round((tabAreaEnd + 4 + xOffset) * scale);
                int sy2 = Math.round((topY + TAB_HEIGHT) * scale);

                LzDraw.enableScissor(context, sx1, sy1, sx2, sy2);
                LzDraw.fill(context, x, topY, x + tabWidth, topY + TAB_HEIGHT, tabBgColor(nextTab, active, panel));
                LzDraw.drawText(context, label, x + 4, topY + 1, tabFgColor(nextTab, active, panel));
                LzDraw.disableScissor(context);
            }
        }

        if (hasRight) {
            int ax = tabAreaEnd;
            LzDraw.fill(context, ax, topY, ax + ARROW_W, topY + TAB_HEIGHT, 0x88444444);
            LzDraw.drawText(context, ">", ax + 2, topY + 1, 0xFFCCCCCC);
        }

        LzDraw.popMatrix(context);
    }

    private static int applyTabOpacity(int color, double panelOp, double globalOp) {
        double op = panelOp >= 0 ? panelOp : globalOp;
        if (op < 0) return color;
        int alpha = (int) Math.round(Math.clamp(op, 0.0, 1.0) * 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int tabBgColor(ChatTab tab, boolean active, ChatPanel panel) {
        int color = (tab.tabColor() == 0)
            ? (active ? 0xBB888888 : 0x88444444)
            : (active ? (0xCC000000 | (tab.tabColor() & 0xFFFFFF)) : (0x55000000 | (tab.tabColor() & 0xFFFFFF)));
        return applyTabOpacity(color, panel.tabBgOpacity(), MultichatClient.CONFIG.tabBgOpacity);
    }

    private static int tabFgColor(ChatTab tab, boolean active, ChatPanel panel) {
        int color;
        if (tab.tabColor() == 0) {
            color = active ? 0xFFFFFFFF : 0xFF999999;
        } else if (active) {
            color = 0xFFFFFFFF;
        } else {
            int rgb = tab.tabColor() & 0xFFFFFF;
            int r = ((rgb >> 16) & 0xFF) * 3 / 5;
            int g = ((rgb >> 8)  & 0xFF) * 3 / 5;
            int b = ( rgb        & 0xFF) * 3 / 5;
            color = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return applyTabOpacity(color, panel.tabFgOpacity(), MultichatClient.CONFIG.tabFgOpacity);
    }

    private static void renderAt(LzDrawContext context,
                                  int currentTick, boolean focused,
                                  List<LzChatLine> visibleMessages, int totalMessages,
                                  int scrolledLines, boolean hasUnread,
                                  int chatWidth, int topY, float scale,
                                  double chatOpacity, double bgOpacity,
                                  int lineHeight, int textOffset, int visibleLineCount,
                                  int hoveredIndex, int xOffset, ChatPanel panel, int lineBgRgb) {
        LzDraw.pushMatrix(context);
        LzDraw.scale(context, scale, scale);
        LzDraw.translate(context, 4.0F + xOffset, 0.0F);

        int linesDrawn = 0;

        for (int r = 0; r + scrolledLines < totalMessages && r < visibleLineCount; r++) {
            int s = r + scrolledLines;
            LzChatLine visible = visibleMessages.get(s);
            if (visible == null) continue;

            int age = currentTick - visible.addedTime();
            if (age >= 200 && !focused) continue;

            double opacityMult = focused ? 1.0 : getOpacityMultiplier(age);
            int textAlpha = (int)(255.0 * opacityMult * chatOpacity);
            int bgAlpha   = (int)(255.0 * opacityMult * bgOpacity);
            linesDrawn++;

            if (textAlpha <= 3) continue;

            int lineTop = topY - r * lineHeight;
            int lineBot = lineTop - lineHeight;
            int textY   = lineTop + textOffset;

            LzDraw.fill(context, -4, lineBot, chatWidth + 4 + 4, lineTop, (bgAlpha << 24) | lineBgRgb);

            LzChatEntry visibleEntry = visible.entry();
            if (!ChatRenderer.highlightEntries.isEmpty()
                    && ChatRenderer.currentTick < ChatRenderer.highlightUntilTick
                    && visibleEntry != null && ChatRenderer.highlightEntries.contains(visibleEntry)) {
                int remaining = ChatRenderer.highlightUntilTick - ChatRenderer.currentTick;
                int hlAlpha = Math.min(120, remaining * 6);
                LzDraw.fill(context, -4, lineBot, chatWidth + 4 + 4, lineTop, (hlAlpha << 24) | 0x00FFAA00);
            }
            if (!ChatRenderer.copyHighlightEntries.isEmpty()
                    && ChatRenderer.currentTick < ChatRenderer.copyHighlightUntil
                    && visibleEntry != null && ChatRenderer.copyHighlightEntries.contains(visibleEntry)) {
                int remaining = ChatRenderer.copyHighlightUntil - ChatRenderer.currentTick;
                int hlAlpha = Math.min(120, remaining * 6);
                LzDraw.fill(context, -4, lineBot, chatWidth + 4 + 4, lineTop, (hlAlpha << 24) | 0x0055CC55);
            }

            int flashAge = currentTick - panel.tabFlashTick();
            if (flashAge >= 0 && flashAge < FLASH_TICKS) {
                float t = (float) flashAge / FLASH_TICKS;
                int flashAlpha = (int)(80 * (1f - t) * (1f - t));
                if (flashAlpha > 0)
                    LzDraw.fill(context, -4, lineBot, chatWidth + 4 + 4, lineTop, (flashAlpha << 24) | 0x00CCCCCC);
            }

            if (MultichatClient.CONFIG.selectionEnabled) {
                int[] selRect = ChatSelection.lineHighlight(visible, s, panel);
                if (selRect != null)
                    LzDraw.fill(context, selRect[0], lineBot, selRect[1], lineTop, 0x7744AAFF);
            }

            if (MultichatClient.CONFIG.showIndicators) {
                LzMessageIndicator indicator = visible.indicator();
                if (indicator != null) {
                    LzDraw.fill(context, -4, lineBot, -2, lineTop, indicator.indicatorColor() | (textAlpha << 24));
                    if (s == hoveredIndex && indicator.hasIcon()) {
                        int iconX = LzDraw.textWidth(visible.content()) + 4;
                        int iconY = textY + 9 - indicator.iconHeight() - 1;
                        indicator.drawIcon(context, iconX, iconY);
                    }
                }
            }

            LzDraw.pushMatrix(context);
            LzDraw.translate(context, 0.0F, 0.0F, 50.0F);
            LzDraw.drawText(context, visible.content(), 0, textY, 16777215 + (textAlpha << 24));
            LzDraw.popMatrix(context);
        }

        long queueCount = LzClient.mc().getMessageHandler().getUnprocessedMessageCount();
        if (queueCount > 0L) {
            int queueTextAlpha = (int)(128.0 * chatOpacity);
            int queueBgAlpha   = (int)(255.0 * bgOpacity);
            LzDraw.pushMatrix(context);
            LzDraw.translate(context, 0.0F, (float) topY);
            LzDraw.fill(context, -2, 0, chatWidth + 4, 9, queueBgAlpha << 24);
            LzDraw.translate(context, 0.0F, 0.0F, 50.0F);
            LzDraw.drawText(context, LzText.translatable("chat.queue", queueCount).ordered(), 0, 1, 16777215 + (queueTextAlpha << 24));
            LzDraw.popMatrix(context);
        }

        if (focused && totalMessages > 0) {
            int totalHeight = totalMessages * lineHeight;
            int drawnHeight = linesDrawn * lineHeight;
            if (totalHeight != drawnHeight) {
                int scrollPos   = scrolledLines * drawnHeight / totalMessages - topY;
                int thumbHeight = Math.max(4, drawnHeight * drawnHeight / totalHeight);
                int scrollX     = chatWidth + 4;
                int alpha       = scrollPos > 0 ? 170 : 96;
                int barColor    = hasUnread ? 13382451 : 3355562;
                LzDraw.fill(context, scrollX,     -scrollPos, scrollX + 2, -scrollPos - thumbHeight, 100, barColor  + (alpha << 24));
                LzDraw.fill(context, scrollX + 1, -scrollPos, scrollX + 2, -scrollPos - thumbHeight, 100, 13421772 + (alpha << 24));
            }
        }

        LzDraw.popMatrix(context);
    }

    public static int computeHoveredIndex(ChatPanel panel, int mouseX, int mouseY,
                                           int visibleLineCount, int windowHeight, float scale,
                                           int lineHeight, int chatWidth, int xOffset, boolean focused) {
        if (!focused) return -1;

        double rawChatX   = mouseX / (double) scale - 4.0;
        double localChatX = rawChatX - xOffset;
        int    topY       = panel.resolveTopY(windowHeight);
        double chatY      = (topY * (double) scale - mouseY) / (scale * lineHeight);

        if (localChatX < -4.0 || localChatX > chatWidth) return -1;

        ChatTab activeTab = panel.getActiveTab();
        if (activeTab == null) return -1;

        List<LzChatLine> visibleMessages = getMessages(panel, activeTab);
        int scrolledLines = panel.getScrolledLines(activeTab);
        int maxLines = Math.min(visibleLineCount, visibleMessages.size());

        if (chatY < 0.0 || chatY >= maxLines) return -1;

        int lineIdx = LzMath.floor(chatY + scrolledLines);
        if (lineIdx < 0 || lineIdx >= visibleMessages.size()) return -1;

        while (lineIdx >= 0) {
            if (visibleMessages.get(lineIdx).endOfEntry()) return lineIdx;
            lineIdx--;
        }
        return -1;
    }

    public static List<LzOrderedText> stampedLines(int creationTick, LzText originalContent,
                                                    LzMessageIndicator indicator,
                                                    List<LzOrderedText> fallback) {
        if (wallTimeStart < 0) return fallback;
        var chat = LzChat.chatHud();
        if (chat == null) return fallback;
        int baseWidth = LzMath.floor((double) LzChat.chatWidth() / LzChat.chatScale());
        return stampedLines(creationTick, originalContent, indicator, baseWidth);
    }

    /** Stamp + wrap at an explicit panel width (already in text-renderer units, indicator-unadjusted). */
    public static List<LzOrderedText> stampedLines(int creationTick, LzText originalContent,
                                                    @org.jetbrains.annotations.Nullable LzMessageIndicator indicator,
                                                    int panelWidth) {
        int indicatorW = indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
        int wrapWidth = panelWidth - indicatorW;
        if (wallTimeStart < 0) return LzChat.breakLines(originalContent, wrapWidth);
        String ts = formatTimestamp(MultichatClient.CONFIG.timestampFormat, getWallTime(creationTick));
        LzText combined = LzText.empty().append(LzText.format(ts + " ")).append(originalContent);
        return LzChat.breakLines(combined, wrapWidth);
    }

    // ── Per-panel message list ─────────────────────────────────────────────────

    /**
     * Lines the given message produces at a specific panel width.
     * Used for secondary-panel scroll adjustment — much cheaper than rebuilding the full width cache.
     */
    public static int lineCountAtWidth(LzText text, @org.jetbrains.annotations.Nullable LzMessageIndicator indicator,
                                        boolean stamped, int tick, int panelWidth) {
        if (stamped) return stampedLines(tick, text, indicator, panelWidth).size();
        int indicatorW = indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
        return LzChat.breakLines(text, panelWidth - indicatorW).size();
    }

    // ── Version-sensitive API isolation ────────────────────────────────────────
    // All logic that depends on specific vanilla methods lives here.
    // When upgrading MC, only update the mixin injection descriptors and these methods.

    /**
     * Result of an indicator hit-test across our panels.
     * NOT_IN_PANEL  → fell through; caller decides based on leftIsVanilla.
     * found=true    → hit a panel (indicator may be null = suppress vanilla).
     */
    public record IndicatorResult(boolean found, @org.jetbrains.annotations.Nullable LzMessageIndicator indicator) {
        public static final IndicatorResult NOT_IN_PANEL = new IndicatorResult(false, null);
    }

    /**
     * Hit-tests all custom panels for a text style at (x, y).
     * Returns the style if found, null otherwise.
     * Caller suppresses vanilla based on leftIsVanilla when null is returned.
     * MC-VERSION-SENSITIVE: called from ChatHudMixin getTextStyleAt injection.
     * If that method is removed, move the call to the replacement hook.
     */
    @org.jetbrains.annotations.Nullable
    public static Style resolveStyleAt(double x, double y, int windowWidth, int windowHeight) {
        for (ChatPanel panel : PANELS) {
            if (!panel.enabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type() == TabType.VANILLA || !active.hasMessages()) continue;

            float scale    = panel.resolveScale();
            int chatWidth  = panel.resolveWidth();
            int lineHeight = panel.resolveLineHeight();
            int panelTopY  = panel.resolveTopY(windowHeight);
            int xOffset    = panel.computeXOffset(windowWidth);

            double localChatX = x / scale - 4.0 - xOffset;
            double chatY      = (panelTopY * (double) scale - y) / (scale * lineHeight);

            if (localChatX < -4.0 || localChatX > chatWidth) continue;

            List<LzChatLine> msgs = getMessages(panel, active);
            int maxLines = Math.min(panel.resolveLines(), msgs.size());
            if (chatY < 0.0 || chatY >= maxLines) continue;

            int lineIdx = LzMath.floor(chatY + panel.getScrolledLines(active));
            if (lineIdx < 0 || lineIdx >= msgs.size()) continue;

            Style style = LzDraw.textHandler()
                .getStyleAt(msgs.get(lineIdx).content().mc(), LzMath.floor(localChatX));
            if (style != null) return style;
        }
        return null;
    }

    /**
     * Hit-tests all custom panels for a message indicator at (mouseX, mouseY).
     * Returns NOT_IN_PANEL if no panel was hit; otherwise returns the indicator result
     * (indicator may be null meaning "in panel, suppress vanilla").
     * MC-VERSION-SENSITIVE: called from ChatHudMixin getIndicatorAt injection.
     * If that method is removed, move the call to the replacement hook.
     */
    public static IndicatorResult resolveIndicatorAt(double mouseX, double mouseY,
                                                      int windowWidth, int windowHeight) {
        for (ChatPanel panel : PANELS) {
            if (!panel.enabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type() == TabType.VANILLA || !active.hasMessages()) continue;

            float scale          = panel.resolveScale();
            int chatWidth        = panel.resolveWidth();
            int lineHeight       = panel.resolveLineHeight();
            int visibleLineCount = panel.resolveLines();
            int xOffset          = panel.computeXOffset(windowWidth);
            int topY             = panel.resolveTopY(windowHeight);

            double chatLineY = (topY * (double) scale - mouseY) / (scale * lineHeight);
            double chatLineX = mouseX / scale - 4.0 - xOffset;
            if (chatLineX < -4.0 || chatLineX > chatWidth) continue;

            List<LzChatLine> panelMsgs = getMessages(panel, active);
            int maxLines = Math.min(visibleLineCount, panelMsgs.size());
            if (chatLineY < 0.0 || chatLineY >= maxLines) continue;

            int lineIdx = LzMath.floor(chatLineY + panel.getScrolledLines(active));
            if (lineIdx < 0 || lineIdx >= panelMsgs.size()) continue;

            while (lineIdx >= 0 && !panelMsgs.get(lineIdx).endOfEntry()) lineIdx--;
            if (lineIdx < 0) continue;

            LzChatLine line = panelMsgs.get(lineIdx);
            LzMessageIndicator indicator = line.indicator();
            if (indicator == null) return new IndicatorResult(true, null);

            if (chatLineX < 0.0) return new IndicatorResult(true, indicator); // over color strip
            if (!indicator.hasIcon()) return new IndicatorResult(true, null);
            int indicatorX = LzDraw.textWidth(line.content()) + 4;
            int iconW = indicator.mc().icon() != null ? indicator.mc().icon().width : 0;
            boolean inside = chatLineX >= indicatorX && chatLineX <= indicatorX + iconW;
            return new IndicatorResult(true, inside ? indicator : null);
        }
        return IndicatorResult.NOT_IN_PANEL;
    }

    /**
     * Called each frame after the hovered message index is known.
     * Currently a no-op placeholder.
     * MC-VERSION-SENSITIVE: in newer MC (StandardCursors API), call
     *   context.setCursor(StandardCursors.POINTING_HAND) when hoveredIndex >= 0
     *   and the hovered line has a clickable/hoverable style.
     */
    public static void updateCursor(LzDrawContext context, ChatPanel panel, int hoveredIndex) {
        // TODO MC upgrade: implement cursor change via StandardCursors when available
    }

    /** Returns the right message list for this panel: primary uses tab.messages(), secondary uses width cache. */
    public static List<LzChatLine> getMessages(ChatPanel panel, ChatTab tab) {
        if (panel.primary()) return tab.messages();
        int width = panel.resolveWidth();
        return tab.getOrBuildWidth(width, w -> buildForWidth(tab, w));
    }

    public static boolean isActiveInAnyPanel(ChatTab tab) {
        for (ChatPanel panel : PANELS) {
            if (panel.enabled() && panel.getActiveTab() == tab) return true;
        }
        return false;
    }

    /** Builds a secondary panel's wrapped message list at {@code panelWidth} from the primary list. */
    private static List<LzChatLine> buildForWidth(ChatTab tab, int panelWidth) {
        List<LzChatLine> result = new ArrayList<>();
        List<LzChatLine> src   = tab.messages();
        int i = 0;
        while (i < src.size()) {
            LzChatLine endLine = src.get(i);
            if (!endLine.endOfEntry()) { i++; continue; }

            int groupEnd = i + 1;
            while (groupEnd < src.size() && !src.get(groupEnd).endOfEntry()) groupEnd++;

            LzChatEntry entry = endLine.entry();
            if (entry != null) {
                List<LzOrderedText> newLines = new ArrayList<>(tab.showTimestamps()
                    ? stampedLines(entry.creationTick, entry.originalContent, entry.indicator, panelWidth)
                    : breakLinesForPanel(entry.originalContent, entry.indicator, panelWidth));

                if (tab instanceof SearchTab st && st.collapseIdentical()) {
                    Integer count = st.collapseCounts().get(entry);
                    if (count != null && count > 1) {
                        String cStr = " " + MultichatClient.CONFIG.collapseCounterFormat
                            .replace("{count}", String.valueOf(count));
                        int last = newLines.size() - 1;
                        newLines.set(last, LzOrderedText.concat(
                            newLines.get(last), LzText.format(cStr).ordered()));
                    }
                }

                List<LzChatLine> group = new ArrayList<>(newLines.size());
                for (int j = 0; j < newLines.size(); j++)
                    group.add(LzChatLine.create(entry.creationTick, newLines.get(j),
                            entry.indicator, j == newLines.size() - 1, entry));
                Collections.reverse(group);
                result.addAll(group);
            } else {
                for (int k = i; k < groupEnd; k++) result.add(src.get(k));
            }
            i = groupEnd;
        }
        return result;
    }

    private static List<LzOrderedText> breakLinesForPanel(LzText content,
                                                            @org.jetbrains.annotations.Nullable LzMessageIndicator indicator,
                                                            int panelWidth) {
        int indicatorW = indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
        return LzChat.breakLines(content, panelWidth - indicatorW);
    }

    private static java.time.LocalTime getWallTime(int addedTick) {
        long millis = wallTimeStart + (long)(addedTick - tickStart) * 50L;
        return java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime();
    }

    static String formatTimestamp(String fmtWithCodes, java.time.LocalTime time) {
        StringBuilder pure = new StringBuilder();
        java.util.List<int[]> inserts = new ArrayList<>();
        for (int i = 0; i < fmtWithCodes.length(); i++) {
            if (fmtWithCodes.charAt(i) == '&' && i + 1 < fmtWithCodes.length()) {
                inserts.add(new int[]{pure.length(), fmtWithCodes.charAt(++i)});
            } else {
                pure.append(fmtWithCodes.charAt(i));
            }
        }

        StringBuilder pat = new StringBuilder();
        boolean quoted = false;
        for (char c : pure.toString().toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                if (quoted) { pat.append('\''); quoted = false; }
                pat.append(c);
            } else {
                if (!quoted) { pat.append('\''); quoted = true; }
                if (c == '\'') pat.append('\'');
                pat.append(c);
            }
        }
        if (quoted) pat.append('\'');

        String formatted;
        try {
            formatted = java.time.format.DateTimeFormatter.ofPattern(pat.toString()).format(time);
        } catch (Exception e) { return "??"; }

        StringBuilder result = new StringBuilder(formatted);
        int offset = 0;
        for (int[] ins : inserts) {
            int pos = Math.min(ins[0] + offset, result.length());
            result.insert(pos, "&" + (char) ins[1]);
            offset += 2;
        }
        return result.toString();
    }

    private static double getOpacityMultiplier(int age) {
        double d = (double) age / 200.0;
        d = 1.0 - d;
        d *= 10.0;
        d = LzMath.clamp(d, 0.0, 1.0);
        d *= d;
        return d;
    }
}
