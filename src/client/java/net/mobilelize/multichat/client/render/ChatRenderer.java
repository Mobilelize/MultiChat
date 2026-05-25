package net.mobilelize.multichat.client.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.mobilelize.fabriclib.client.LzChat;
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
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.mixin.client.ChatHudAccessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatRenderer {

    public static final List<ChatPanel> PANELS = new ArrayList<>();

    // Convenience references — same objects as PANELS.get(0) and PANELS.get(1)
    public static final ChatPanel LEFT;
    public static final ChatPanel RIGHT;

    static {
        LEFT         = new ChatPanel();
        LEFT.primary = true;
        LEFT.enabled = true;
        LEFT.id      = "panel0";
        PANELS.add(LEFT);

        RIGHT        = new ChatPanel();
        RIGHT.anchor = ChatPanel.Anchor.RIGHT;
        RIGHT.id     = "panel1";
        PANELS.add(RIGHT);
    }

    public static ChatPanel primaryPanel() { return PANELS.getFirst(); }

    public static ChatTab contextTab = null;

    public static int             currentTick        = 0;
    public static Set<LzChatLine> highlightLines     = new HashSet<>();
    public static int             highlightUntilTick = 0;
    public static Set<LzChatLine> copyHighlightLines = new HashSet<>();
    public static int             copyHighlightUntil = 0;

    private static long wallTimeStart = -1;
    private static int  tickStart     = 0;

    public static final List<ChatTab> TABS = new ArrayList<>(List.of(
        new ChatTab("All",     TabType.ALL,     List.of("panel0", "panel1")),
        new ChatTab("Vanilla", TabType.VANILLA, List.of("panel0"))
    ));

    public static int lastMouseX = 0;

    public static final int TAB_HEIGHT = 10;
    public static final int ARROW_W    = 10;

    public static int getTabBarY(ChatPanel panel, int topY, int visibleLineCount, int lineHeight) {
        return panel.tabsAtTop ? topY - visibleLineCount * lineHeight - TAB_HEIGHT : topY;
    }

    public static String getTabLabel(ChatPanel panel, ChatTab tab) {
        if (panel.showBadges && tab.showBadge && tab.unreadCount > 0)
            return tab.name + " [" + tab.unreadCount + "]";
        return tab.name;
    }

    public static void resetForRefresh() {
        PANELS.forEach(ChatPanel::resetScrollPositions);
        TABS.forEach(t -> {
            t.messages.clear();
            t.unreadCount = 0;
            t.resetCollapse();
        });
    }

    public static void render(ChatHud chat, DrawContext context, int currentTick,
                               int mouseX, int mouseY, boolean focused, boolean skipLeftMessages) {
        ChatHudAccessor accessor = (ChatHudAccessor) chat;

        lastMouseX = mouseX;
        ChatRenderer.currentTick = currentTick;
        if (wallTimeStart < 0) { wallTimeStart = System.currentTimeMillis(); tickStart = currentTick; }

        if (LzOptions.chatVisibility() == LzChatVisibility.HIDDEN) return;

        boolean anyVisible = PANELS.stream()
            .anyMatch(p -> p.enabled && (!p.showOnlyWhenFocused || focused));
        if (!anyVisible) return;

        LzClient.mc().getProfiler().push("multichat");

        int windowHeight   = LzDraw.scaledHeight(context);
        int windowWidth    = LzDraw.scaledWidth(context);
        double lineSpacing = LzOptions.chatLineSpacing();
        boolean hasUnread  = accessor.getHasUnreadNewMessages();

        for (ChatPanel panel : PANELS) {
            if (!panel.enabled) continue;
            if (panel.showOnlyWhenFocused && !focused) continue;

            ChatTab activeTab = panel.getActiveTab();
            if (activeTab == null) continue;

            float scale          = panel.resolveScale();
            int chatWidth        = panel.resolveWidth();
            double chatOpacity   = panel.resolveChatOpacity();
            double bgOpacity     = panel.resolveBgOpacity();
            int lineHeight       = panel.resolveLineHeight();
            int textOffset       = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
            int visibleLineCount = panel.resolveLines();
            int topY             = panel.resolveTopY(windowHeight);
            int xOffset          = panel.computeXOffset(windowWidth);

            int tabY = getTabBarY(panel, topY, visibleLineCount, lineHeight);
            if (focused && panel.showTabs) renderTabBar(context, panel, xOffset, tabY, scale, chatWidth);

            if (activeTab.type == TabType.VANILLA) continue;
            if (panel == LEFT && skipLeftMessages) continue;

            List<LzChatLine> msgs = activeTab.messages;
            if (msgs.isEmpty()) continue;

            int hoveredIndex = computeHoveredIndex(panel, mouseX, mouseY,
                    visibleLineCount, windowHeight, scale, lineHeight, chatWidth, xOffset, focused);

            int lineBgRgb = (activeTab.msgBgColor != 0 ? activeTab.msgBgColor : panel.bgColor) & 0xFFFFFF;
            renderAt(context, currentTick, focused,
                    msgs, msgs.size(), panel.getScrolledLines(activeTab), hasUnread,
                    chatWidth, topY, scale, chatOpacity, bgOpacity,
                    lineHeight, textOffset, visibleLineCount, hoveredIndex, xOffset, panel, lineBgRgb);
        }

        LzClient.mc().getProfiler().pop();
    }

    private static void renderTabBar(DrawContext context,
                                      ChatPanel panel, int xOffset, int topY, float scale, int chatWidth) {
        LzDraw.pushMatrix(context);
        LzDraw.scale(context, scale, scale);
        LzDraw.translate(context, 4.0F + xOffset, 0.0F);

        List<ChatTab> visibleTabs = panel.getVisibleTabs();
        panel.tabScrollOffset = LzMath.clamp(panel.tabScrollOffset, 0, Math.max(0, visibleTabs.size() - 1));

        boolean hasLeft = panel.tabScrollOffset > 0;

        int simX = hasLeft ? -4 + ARROW_W : -4;
        int simEnd = chatWidth + 4 - ARROW_W;
        boolean hasRight = false;
        for (int i = panel.tabScrollOffset; i < visibleTabs.size(); i++) {
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

        int lastRendered = panel.tabScrollOffset - 1;
        for (int i = panel.tabScrollOffset; i < visibleTabs.size(); i++) {
            ChatTab tab = visibleTabs.get(i);
            String label = getTabLabel(panel, tab);
            int tabWidth = LzDraw.textWidth(label) + 8;
            if (x + tabWidth > tabAreaEnd) break;

            boolean active = (i == panel.activeTab);
            LzDraw.fill(context, x, topY, x + tabWidth, topY + TAB_HEIGHT, tabBgColor(tab, active));
            LzDraw.drawText(context, label, x + 4, topY + 1, tabFgColor(tab, active));
            x += tabWidth + 1;
            lastRendered = i;
        }
        panel.tabLastRendered = lastRendered;

        if (hasRight && lastRendered + 1 < visibleTabs.size()) {
            int remaining = tabAreaEnd - x;
            if (remaining > 4) {
                ChatTab nextTab = visibleTabs.get(lastRendered + 1);
                String label = getTabLabel(panel, nextTab);
                int tabWidth = LzDraw.textWidth(label) + 8;
                boolean active = (lastRendered + 1 == panel.activeTab);

                int sx1 = Math.round((x + 4 + xOffset) * scale);
                int sy1 = Math.round(topY * scale);
                int sx2 = Math.round((tabAreaEnd + 4 + xOffset) * scale);
                int sy2 = Math.round((topY + TAB_HEIGHT) * scale);

                LzDraw.enableScissor(context, sx1, sy1, sx2, sy2);
                LzDraw.fill(context, x, topY, x + tabWidth, topY + TAB_HEIGHT, tabBgColor(nextTab, active));
                LzDraw.drawText(context, label, x + 4, topY + 1, tabFgColor(nextTab, active));
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

    private static int tabBgColor(ChatTab tab, boolean active) {
        if (tab.tabColor == 0) return active ? 0xBB888888 : 0x88444444;
        return active ? (0xCC000000 | (tab.tabColor & 0xFFFFFF)) : (0x55000000 | (tab.tabColor & 0xFFFFFF));
    }

    private static int tabFgColor(ChatTab tab, boolean active) {
        if (tab.tabColor == 0) return active ? 0xFFFFFFFF : 0xFF999999;
        if (active) return 0xFFFFFFFF;
        int rgb = tab.tabColor & 0xFFFFFF;
        int r = ((rgb >> 16) & 0xFF) * 3 / 5;
        int g = ((rgb >> 8)  & 0xFF) * 3 / 5;
        int b = ( rgb        & 0xFF) * 3 / 5;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void renderAt(DrawContext context,
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

            if (!ChatRenderer.highlightLines.isEmpty()
                    && ChatRenderer.currentTick < ChatRenderer.highlightUntilTick
                    && ChatRenderer.highlightLines.contains(visible)) {
                int remaining = ChatRenderer.highlightUntilTick - ChatRenderer.currentTick;
                int hlAlpha = Math.min(120, remaining * 6);
                LzDraw.fill(context, -4, lineBot, chatWidth + 4 + 4, lineTop, (hlAlpha << 24) | 0x00FFAA00);
            }
            if (!ChatRenderer.copyHighlightLines.isEmpty()
                    && ChatRenderer.currentTick < ChatRenderer.copyHighlightUntil
                    && ChatRenderer.copyHighlightLines.contains(visible)) {
                int remaining = ChatRenderer.copyHighlightUntil - ChatRenderer.currentTick;
                int hlAlpha = Math.min(120, remaining * 6);
                LzDraw.fill(context, -4, lineBot, chatWidth + 4 + 4, lineTop, (hlAlpha << 24) | 0x0055CC55);
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

        if (focused) {
            int totalHeight = totalMessages * lineHeight;
            int drawnHeight = linesDrawn * lineHeight;
            if (totalHeight != drawnHeight) {
                int scrollPos   = scrolledLines * drawnHeight / totalMessages - topY;
                int thumbHeight = drawnHeight * drawnHeight / totalHeight;
                int scrollX     = chatWidth + 4;
                int alpha       = scrollPos > 0 ? 170 : 96;
                int barColor    = hasUnread ? 13382451 : 3355562;
                LzDraw.fill(context, scrollX,     -scrollPos, scrollX + 2, -scrollPos - thumbHeight, 100, barColor  + (alpha << 24));
                LzDraw.fill(context, scrollX + 2, -scrollPos, scrollX + 1, -scrollPos - thumbHeight, 100, 13421772 + (alpha << 24));
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

        List<LzChatLine> visibleMessages = activeTab.messages;
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
                                                    MessageIndicator indicator,
                                                    List<LzOrderedText> fallback) {
        if (wallTimeStart < 0) return fallback;
        var chat = LzChat.chatHud();
        if (chat == null) return fallback;
        int baseWidth = LzMath.floor((double) LzChat.chatWidth() / LzChat.chatScale());
        int width = baseWidth - (indicator != null && indicator.icon() != null
            ? indicator.icon().width + 4 + 2 : 0);
        String ts = formatTimestamp(MultichatClient.CONFIG.timestampFormat, getWallTime(creationTick));
        LzText combined = LzText.empty().append(LzText.format(ts + " ")).append(originalContent);
        return LzChat.breakLines(combined, width);
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
