package net.mobilelize.multichat.client.render;

import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.profiler.Profilers;
import net.mobilelize.fabriclib.client.*;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.integration.chatheads.ChatHeadsSupport;
import net.mobilelize.multichat.mixin.client.ChatHudAccessor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatRenderer {

    public static int             currentTick          = 0;
    public static Set<LzChatEntry> highlightEntries    = new HashSet<>();
    public static int              highlightUntilTick  = 0;
    public static Set<LzChatEntry> copyHighlightEntries = new HashSet<>();
    public static int              copyHighlightUntil  = 0;

    public static int lastMouseX = 0;

    // lastHoveredStyle: set per-frame via DrawnTextConsumer callback in renderAt().
    // lastHoveredIndicator: set per-frame via resolveIndicatorAt() in render().
    public static @Nullable Style           lastHoveredStyle     = null;
    public static @Nullable IndicatorResult lastHoveredIndicator = null;

    public static final int TAB_HEIGHT = 10;
    public static final int ARROW_W    = 10;
    private static final int FLASH_TICKS = 12;

    public static int getTabBarY(ChatPanel panel, int topY, int visibleLineCount, int lineHeight) {
        return panel.tabsAtTop() ? topY - visibleLineCount * lineHeight - TAB_HEIGHT : topY;
    }

    /** Width of the red "X" back button shown first in the tab bar while drilled into a multi-tab's
     *  sub-tabs. Shared by ChatRenderer (draw) and ChatScreenMixin (click) so they can't drift apart. */
    public static int backButtonWidth() { return LzDraw.textWidth("X") + 6; }

    public static String getTabLabel(ChatPanel panel, ChatTab tab) {
        String label = (panel.showBadges() && tab.showBadge() && tab.hasUnread())
            ? tab.name() + " [" + tab.unreadCount() + "]"
            : tab.name();
        if (tab.isMultiTab()) label = "✻" + label;
        return label;
    }

    public static void render(ChatHud chat, LzDrawContext context, int currentTick,
                               int mouseX, int mouseY, boolean focused, boolean skipLeftMessages) {
        lastHoveredStyle     = null;
        lastHoveredIndicator = null;
        ChatHudAccessor accessor = (ChatHudAccessor) chat;

        lastMouseX = mouseX;
        ChatRenderer.currentTick = currentTick;
        ChatManager.initWallTime(currentTick);

        // Applied here (every rendered frame) instead of the tick loop (fixed 20Hz) so a finished
        // background rewrap shows up on the very next frame instead of waiting on a tick boundary.
        // Drained even if chat ends up not visible this frame — no reason to sit on a finished swap.
        WrapCoordinator.drain(32);

        if (LzOptions.chatVisibility() == LzChatVisibility.HIDDEN) return;

        boolean anyVisible = ChatManager.getAllPanels().stream().anyMatch(p -> p.isVisibleInChat(focused));
        if (!anyVisible) return;

        Profilers.get().push("multichat");

        int windowHeight   = LzDraw.scaledHeight(context);
        int windowWidth    = LzDraw.scaledWidth(context);
        double lineSpacing = LzOptions.chatLineSpacing();
        boolean hasUnread  = accessor.getHasUnreadNewMessages();

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isVisibleInChat(focused)) continue;

            ChatTab activeTab = panel.getActiveTab();
            if (activeTab == null) continue;

            // "Always Focused" panels render at full opacity/full line-count permanently, same as
            // NoteTab.renderOutOfFocus() already does for note tabs — folds into the same flag so
            // resolveLines() (line count) and the fade/opacity pass below both pick it up.
            boolean effectiveFocused = focused || panel.alwaysFocused()
                || (activeTab instanceof NoteTab nt && nt.renderOutOfFocus());

            float scale          = panel.resolveScale();
            int chatWidth        = panel.resolveWidth();       // tab bar — never extends for chat_heads
            int renderWidth      = panel.resolveRenderWidth(); // message area — primary panel may extend
            double chatOpacity   = panel.resolveChatOpacity();
            double bgOpacity     = panel.resolveBgOpacity();
            int lineHeight       = panel.resolveLineHeight();
            int textOffset       = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
            int visibleLineCount = panel.resolveLines(effectiveFocused);
            int topY             = panel.resolveTopY(windowHeight);
            int xOffset          = panel.computeXOffset(windowWidth);

            int tabY = getTabBarY(panel, topY, visibleLineCount, lineHeight);
            if (focused && panel.showTabs()) renderTabBar(context, panel, xOffset, tabY, scale, chatWidth);

            if (activeTab.type() == TabType.VANILLA) continue;
            if (panel == ChatManager.primaryPanel() && skipLeftMessages) continue;

            List<LzChatLine> msgs = panel.getTabLines(activeTab);
            if (msgs == null || msgs.isEmpty()) continue;

            int hoveredIndex = computeHoveredIndex(panel, mouseX, mouseY,
                    visibleLineCount, windowHeight, scale, lineHeight, renderWidth, xOffset, focused);
            int panelBg   = panel.bgColor() != 0 ? panel.bgColor()
                    : MultichatClient.CONFIG.defaultPanelBgColor != 0 ? MultichatClient.CONFIG.defaultPanelBgColor
                    : ThemeRegistry.active().defaultPanelBg;
            int lineBgRgb = (activeTab.msgBgColor() != 0 ? activeTab.msgBgColor() : panelBg) & 0xFFFFFF;
            renderAt(context, currentTick, effectiveFocused,
                    msgs, msgs.size(), panel.getScrolledLines(activeTab), hasUnread,
                    renderWidth, topY, scale, chatOpacity, bgOpacity,
                    lineHeight, textOffset, visibleLineCount, hoveredIndex, xOffset, panel, lineBgRgb,
                    focused, mouseX, mouseY, windowWidth, windowHeight);
        }

        if (focused) {
            lastHoveredIndicator = resolveIndicatorAt(mouseX, mouseY, windowWidth, windowHeight);
        }

        Profilers.get().pop();
    }

    private static void renderTabBar(LzDrawContext context,
                                      ChatPanel panel, int xOffset, int topY, float scale, int chatWidth) {
        LzDraw.pushMatrix(context);
        LzDraw.scale(context, scale, scale);
        LzDraw.translate(context, 4.0F + xOffset, 0.0F);

        List<ChatTab> visibleTabs = panel.getVisibleTabs();
        panel.setTabScrollOffset(LzMath.clamp(panel.tabScrollOffset(), 0, Math.max(0, visibleTabs.size() - 1)));

        boolean hasLeft = panel.tabScrollOffset() > 0;
        int backW = panel.isExpanded() ? backButtonWidth() : 0;

        int simX = -4 + backW + (hasLeft ? ARROW_W : 0);
        int simEnd = chatWidth + 4 - ARROW_W;
        boolean hasRight = false;
        for (int i = panel.tabScrollOffset(); i < visibleTabs.size(); i++) {
            int tw = LzDraw.textWidth(getTabLabel(panel, visibleTabs.get(i))) + 8;
            if (simX + tw > simEnd) { hasRight = true; break; }
            simX += tw + 1;
        }

        int tabAreaEnd = chatWidth + 4 - (hasRight ? ARROW_W : 0);

        ChatTheme theme = ThemeRegistry.active();

        int x = -4;
        if (panel.isExpanded()) {
            LzDraw.drawText(context, "X", x + 3, topY + 1, 0xFFFF5555);
            x += backW;
        }
        if (hasLeft) {
            LzDraw.fill(context, x, topY, x + ARROW_W, topY + TAB_HEIGHT, theme.tabBgInactive);
            LzDraw.drawText(context, "<", x + 2, topY + 1, theme.tabArrowText);
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
        if (panel.consumePendingScrollIntoView()) {
            int active = panel.activeTabIndex();
            if (active < panel.tabScrollOffset()) {
                panel.setTabScrollOffset(active);
            } else if (active > lastRendered) {
                int fullWidth = chatWidth + 8; // x range [-4, chatWidth+4]
                int usedWidth = 0;
                int newOffset = active;
                for (int i = active; i >= 0; i--) {
                    int tw = LzDraw.textWidth(getTabLabel(panel, visibleTabs.get(i))) + 8;
                    if (i < active) usedWidth += 1; // gap between tabs
                    usedWidth += tw;
                    int available = fullWidth - (i > 0 ? ARROW_W : 0);
                    if (usedWidth > available) break;
                    newOffset = i;
                }
                panel.setTabScrollOffset(newOffset);
            }
        }

        if (hasRight && lastRendered + 1 < visibleTabs.size()) {
            int remaining = tabAreaEnd - x;
            if (remaining > 4) {
                ChatTab nextTab = visibleTabs.get(lastRendered + 1);
                String label = getTabLabel(panel, nextTab);
                int tabWidth = LzDraw.textWidth(label) + 8;
                boolean active = (lastRendered + 1 == panel.activeTabIndex());

                LzDraw.enableScissor(context, x, topY, tabAreaEnd, topY + TAB_HEIGHT);
                LzDraw.fill(context, x, topY, x + tabWidth, topY + TAB_HEIGHT, tabBgColor(nextTab, active, panel));
                LzDraw.drawText(context, label, x, topY + 1, tabFgColor(nextTab, active, panel));
                LzDraw.disableScissor(context);
            }
        }

        if (hasRight) {
            int ax = tabAreaEnd;
            LzDraw.fill(context, ax, topY, ax + ARROW_W, topY + TAB_HEIGHT, theme.tabBgInactive);
            LzDraw.drawText(context, ">", ax + 2, topY + 1, theme.tabArrowText);
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
        ChatTheme theme = ThemeRegistry.active();
        int color = (tab.tabColor() == 0)
            ? (active ? theme.tabBgActive : theme.tabBgInactive)
            : (active ? (theme.tabCustomAlphaActive | (tab.tabColor() & 0xFFFFFF)) : (theme.tabCustomAlphaInactive | (tab.tabColor() & 0xFFFFFF)));
        return applyTabOpacity(color, panel.tabBgOpacity(), MultichatClient.CONFIG.tabBgOpacity);
    }

    private static int tabFgColor(ChatTab tab, boolean active, ChatPanel panel) {
        ChatTheme theme = ThemeRegistry.active();
        int color;
        if (tab.tabColor() == 0) {
            color = active ? theme.tabTextActive : theme.tabTextInactive;
        } else if (active) {
            color = theme.tabTextActive;
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
                                  int hoveredIndex, int xOffset, ChatPanel panel, int lineBgRgb,
                                  boolean showScrollbar, int mouseX, int mouseY, int windowWidth, int windowHeight) {
        LzDraw.pushMatrix(context);
        LzDraw.scale(context, scale, scale);
        LzDraw.translate(context, 4.0F + xOffset, 0.0F);

        ChatTheme theme = ThemeRegistry.active();

        DrawnTextConsumer hoverDrawer = null;
        DrawnTextConsumer.Transformation hoverTransform = null;
        Style[] hoverCapture = { null };
        if (showScrollbar) {
            DrawContext rawCtx = context.mc();
            hoverDrawer = rawCtx.getTextConsumer(
                DrawContext.HoverType.TOOLTIP_AND_CURSOR,
                style -> { if (hoverCapture[0] == null) hoverCapture[0] = style; }
            );
            hoverTransform = hoverDrawer.getTransformation()
                .withPose(new Matrix3x2f(rawCtx.getMatrices()));
        }

        record Row(int s, LzChatLine visible, int lineTop, int lineBot, int textY, int textAlpha, int bgAlpha) {}

        List<Row> rows = new ArrayList<>();
        int maxR = Math.min(visibleLineCount, totalMessages - scrolledLines) - 1;
        for (int r = maxR; r >= 0; r--) {
            int s = r + scrolledLines;
            LzChatLine visible = visibleMessages.get(s);
            if (visible == null) continue;

            int age = currentTick - visible.addedTime();
            if (age >= 200 && !focused) continue;

            double opacityMult = focused ? 1.0 : getOpacityMultiplier(age);
            int textAlpha = (int)(255.0 * opacityMult * chatOpacity);
            int bgAlpha   = (int)(255.0 * opacityMult * bgOpacity);

            if (textAlpha <= 3) continue;

            int lineTop = topY - r * lineHeight;
            int lineBot = lineTop - lineHeight;
            int textY   = lineTop + textOffset;

            rows.add(new Row(s, visible, lineTop, lineBot, textY, textAlpha, bgAlpha));
        }
        int linesDrawn = rows.size();

        // Both passes below query glyph widths (Pass 1's lineHighlight/selection math, Pass 2's
        // indicator icon offset and the actual text draw) that must agree with each other and
        // with the wrap-time measurement in ChatPanel.wrapEntryInto — one begin/end bracket
        // around both, rather than per-call, so they can't drift out of sync with each other.
        ChatHeadsSupport.beginHeadRendering();
        try {
            // Pass 1: all background-layer fills, for every row, before any text is drawn.
            for (Row row : rows) {
                LzDraw.fill(context, -4, row.lineBot(), chatWidth + 4 + 4, row.lineTop(), (row.bgAlpha() << 24) | lineBgRgb);

                LzChatEntry visibleEntry = row.visible().entry();
                if (!ChatRenderer.highlightEntries.isEmpty()
                        && ChatRenderer.currentTick < ChatRenderer.highlightUntilTick
                        && visibleEntry != null && ChatRenderer.highlightEntries.contains(visibleEntry)) {
                    int remaining = ChatRenderer.highlightUntilTick - ChatRenderer.currentTick;
                    int hlAlpha = Math.min(120, remaining * 6);
                    LzDraw.fill(context, -4, row.lineBot(), chatWidth + 4 + 4, row.lineTop(), (hlAlpha << 24) | theme.highlightSearchRgb);
                }
                if (!ChatRenderer.copyHighlightEntries.isEmpty()
                        && ChatRenderer.currentTick < ChatRenderer.copyHighlightUntil
                        && visibleEntry != null && ChatRenderer.copyHighlightEntries.contains(visibleEntry)) {
                    int remaining = ChatRenderer.copyHighlightUntil - ChatRenderer.currentTick;
                    int hlAlpha = Math.min(120, remaining * 6);
                    LzDraw.fill(context, -4, row.lineBot(), chatWidth + 4 + 4, row.lineTop(), (hlAlpha << 24) | theme.highlightNewRgb);
                }

                int flashAge = currentTick - panel.tabFlashTick();
                if (flashAge >= 0 && flashAge < FLASH_TICKS) {
                    float t = (float) flashAge / FLASH_TICKS;
                    int flashAlpha = (int)(80 * (1f - t) * (1f - t));
                    if (flashAlpha > 0)
                        LzDraw.fill(context, -4, row.lineBot(), chatWidth + 4 + 4, row.lineTop(), (flashAlpha << 24) | theme.highlightFlashRgb);
                }

                if (MultichatClient.CONFIG.selectionEnabled) {
                    int[] selRect = ChatSelection.lineHighlight(row.visible(), row.s(), panel);
                    if (selRect != null)
                        LzDraw.fill(context, selRect[0], row.lineBot(), selRect[1], row.lineTop(), theme.selection);
                }
            }

            // Pass 2: indicators + text, for every row, always drawn on top of every background above.
            for (Row row : rows) {
                LzChatLine visible = row.visible();
                int textAlpha = row.textAlpha();
                int textY     = row.textY();

                if (visible.isSkeleton()) {
                    // Whole tab is wrapping on a background thread (see WrapCoordinator) — draw a
                    // plain loading bar instead of real text. Width is a deterministic pseudo-random
                    // spread (keyed off the row) so a block of placeholders doesn't read as one
                    // solid rectangle.
                    int seed = row.s() * 0x9E3779B1;
                    int barW = 30 + Math.floorMod(seed, Math.max(1, chatWidth - 40));
                    int barAlpha = (int)(textAlpha * 0.35);
                    if (barAlpha > 2)
                        LzDraw.fill(context, 0, textY, barW, textY + 8, (barAlpha << 24) | (theme.messageTextRgb & 0xFFFFFF));
                    continue;
                }

                // chat_heads' BEFORE_LINE head texture (if any) only ever sits on the entry's
                // first wrapped line — see ChatHeadsSupport's BEFORE_LINE support section. But the
                // reserved INDENT applies per-line, independent of whether that specific line has
                // a head: every continuation line of a wrapped message gets the same "no head of
                // my own" offset any headless message's first line would (emptyLineWidth()),
                // driven purely by chat_heads' "offset non-player text" alignment setting, so a
                // multi-line message's lines all stay flush with each other and with the head.
                // Gated on the entry having gone through chat_heads' pipeline at all (get() !=
                // NONE) — MultiChat's own synthetic lines (Notes/Info tabs, custom raw text) never
                // do, and shouldn't pick up alignment padding meant for real chat messages.
                ChatHeadsSupport.HeadRef headRef = ChatHeadsSupport.get(visible.entry());
                int headOffset = 0;
                if (headRef != ChatHeadsSupport.HeadRef.NONE) {
                    if (visible.startOfEntry()) {
                        headOffset = ChatHeadsSupport.headWidth(headRef);
                        if (headOffset > 0) {
                            ChatHeadsSupport.renderHead(context.mc(), 0, textY, headRef, textAlpha / 255.0f);
                        }
                    } else {
                        headOffset = ChatHeadsSupport.emptyLineWidth();
                    }
                }

                if (MultichatClient.CONFIG.showIndicators) {
                    LzMessageIndicator indicator = visible.indicator();
                    if (indicator != null) {
                        LzDraw.fill(context, -4, row.lineBot(), -2, row.lineTop(), indicator.indicatorColor() | (textAlpha << 24));
                        if (row.s() == hoveredIndex && indicator.hasIcon()) {
                            int iconX = headOffset + LzDraw.textWidth(visible.content()) + 4;
                            int iconY = textY + 9 - indicator.iconHeight() - 1;
                            indicator.drawIcon(context, iconX, iconY);
                        }
                    }
                }

                if (hoverDrawer != null && hoverTransform != null) {
                    hoverDrawer.text(Alignment.LEFT, headOffset, textY,
                        hoverTransform.withOpacity(textAlpha / 255.0f), visible.content().mc());
                } else {
                    LzDraw.pushMatrix(context);
                    LzDraw.translate(context, 0.0F, 0.0F, 50.0F);
                    LzDraw.drawText(context, visible.content(), headOffset, textY, theme.messageTextRgb + (textAlpha << 24));
                    LzDraw.popMatrix(context);
                }
            }
        } finally {
            ChatHeadsSupport.endHeadRendering();
        }

        long queueCount = LzClient.mc().getMessageHandler().getUnprocessedMessageCount();
        if (queueCount > 0L) {
            int queueTextAlpha = (int)(128.0 * chatOpacity);
            int queueBgAlpha   = (int)(255.0 * bgOpacity);
            LzDraw.pushMatrix(context);
            LzDraw.translate(context, 0.0F, (float) topY);
            LzDraw.fill(context, -2, 0, chatWidth + 4, 9, queueBgAlpha << 24);
            LzDraw.translate(context, 0.0F, 0.0F, 50.0F);
            LzDraw.drawText(context, LzText.translatable("chat.queue", queueCount).ordered(), 0, 1, theme.messageTextRgb + (queueTextAlpha << 24));
            LzDraw.popMatrix(context);
        }

        if (showScrollbar && totalMessages > 0) {
            int totalHeight = totalMessages * lineHeight;
            int drawnHeight = linesDrawn * lineHeight;
            if (totalHeight != drawnHeight) {
                int scrollPos   = scrolledLines * drawnHeight / totalMessages - topY;
                int thumbHeight = Math.max(4, drawnHeight * drawnHeight / totalHeight);
                int scrollX     = chatWidth + 4;

                boolean scrollbarActive = MultichatClient.CONFIG.scrollbarGlowEnabled
                    && ((ChatScrollbarDrag.isDragging() && ChatScrollbarDrag.panel() == panel)
                        || ChatScrollbarDrag.isMouseOverRegion(panel, mouseX, mouseY, windowWidth, windowHeight));

                if (scrollbarActive) {
                    // soft halo a hair wider than the thumb itself — doesn't need to be pixel-precise,
                    // just needs to read as "this whole area is grabbable"
                    int glowPad = 1;
                    LzDraw.fill(context, scrollX - glowPad, -scrollPos + glowPad, scrollX + 2 + glowPad,
                        -scrollPos - thumbHeight - glowPad, 99, theme.scrollbarGlow);
                }

                int alpha    = scrollbarActive ? 230 : (scrollPos > 0 ? 170 : 96);
                int barColor = scrollbarActive ? theme.scrollbarActiveRgb
                    : (hasUnread ? theme.scrollbarUnreadRgb : theme.scrollbarNoUnreadRgb);
                LzDraw.fill(context, scrollX,     -scrollPos, scrollX + 2, -scrollPos - thumbHeight, 100, barColor  + (alpha << 24));
                LzDraw.fill(context, scrollX + 1, -scrollPos, scrollX + 2, -scrollPos - thumbHeight, 100, theme.scrollbarHighlightRgb + (alpha << 24));
            }
        }

        if (hoverCapture[0] != null) lastHoveredStyle = hoverCapture[0];
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

        if (localChatX < -4.0 || localChatX > chatWidth + 8) return -1; // see ChatSelection.hitLine

        ChatTab activeTab = panel.getActiveTab();
        if (activeTab == null) return -1;

        List<LzChatLine> visibleMessages = panel.getTabLines(activeTab);
        if (visibleMessages == null) return -1;
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

    // ── Version-sensitive API isolation ────────────────────────────────────────

    public record IndicatorResult(boolean found, @Nullable LzMessageIndicator indicator) {
        public static final IndicatorResult NOT_IN_PANEL = new IndicatorResult(false, null);
    }

    @Nullable
    public static Style resolveStyleAt(double x, double y, int windowWidth, int windowHeight) {
        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type() == TabType.VANILLA || !active.hasMessages()) continue;

            float scale    = panel.resolveScale();
            int chatWidth  = panel.resolveRenderWidth();
            int lineHeight = panel.resolveLineHeight();
            int panelTopY  = panel.resolveTopY(windowHeight);
            int xOffset    = panel.computeXOffset(windowWidth);

            double localChatX = x / scale - 4.0 - xOffset;
            double chatY      = (panelTopY * (double) scale - y) / (scale * lineHeight);

            if (localChatX < -4.0 || localChatX > chatWidth + 8) continue; // see ChatSelection.hitLine

            List<LzChatLine> msgs = panel.getTabLines(active);
            if (msgs == null) continue;
            int maxLines = Math.min(panel.resolveLines(), msgs.size());
            if (chatY < 0.0 || chatY >= maxLines) continue;

            int lineIdx = LzMath.floor(chatY + panel.getScrolledLines(active));
            if (lineIdx < 0 || lineIdx >= msgs.size()) continue;

            Style style = getStyleAt(msgs.get(lineIdx).content().mc(), LzMath.floor(localChatX));
            if (style != null) return style;
        }
        return null;
    }

    private static @org.jetbrains.annotations.Nullable Style getStyleAt(OrderedText text, int x) {
        net.minecraft.client.font.TextHandler handler = LzDraw.textHandler();
        float[] w = { 0 };
        Style[] out = { null };
        text.accept((index, style, codePoint) -> {
            w[0] += handler.getWidth(v -> v.accept(0, style, codePoint));
            if (w[0] > x) { out[0] = style; return false; }
            return true;
        });
        return out[0];
    }

    public static IndicatorResult resolveIndicatorAt(double mouseX, double mouseY,
                                                      int windowWidth, int windowHeight) {
        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type() == TabType.VANILLA || !active.hasMessages()) continue;

            float scale          = panel.resolveScale();
            int chatWidth        = panel.resolveRenderWidth();
            int lineHeight       = panel.resolveLineHeight();
            int visibleLineCount = panel.resolveLines();
            int xOffset          = panel.computeXOffset(windowWidth);
            int topY             = panel.resolveTopY(windowHeight);

            double chatLineY = (topY * (double) scale - mouseY) / (scale * lineHeight);
            double chatLineX = mouseX / scale - 4.0 - xOffset;
            if (chatLineX < -4.0 || chatLineX > chatWidth + 8) continue; // see ChatSelection.hitLine

            List<LzChatLine> panelMsgs = panel.getTabLines(active);
            if (panelMsgs == null) continue;
            int maxLines = Math.min(visibleLineCount, panelMsgs.size());
            if (chatLineY < 0.0 || chatLineY >= maxLines) continue;

            int lineIdx = LzMath.floor(chatLineY + panel.getScrolledLines(active));
            if (lineIdx < 0 || lineIdx >= panelMsgs.size()) continue;

            while (lineIdx >= 0 && !panelMsgs.get(lineIdx).endOfEntry()) lineIdx--;
            if (lineIdx < 0) continue;

            LzChatLine line = panelMsgs.get(lineIdx);
            LzMessageIndicator indicator = line.indicator();
            if (indicator == null) return new IndicatorResult(true, null);

            if (chatLineX < 0.0) return new IndicatorResult(true, indicator);
            if (!indicator.hasIcon()) return new IndicatorResult(true, null);
            int indicatorX = LzDraw.textWidth(line.content()) + 4;
            int iconW = indicator.mc().icon() != null ? indicator.mc().icon().width : 0;
            boolean inside = chatLineX >= indicatorX && chatLineX <= indicatorX + iconW;
            return new IndicatorResult(true, inside ? indicator : null);
        }
        return IndicatorResult.NOT_IN_PANEL;
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
