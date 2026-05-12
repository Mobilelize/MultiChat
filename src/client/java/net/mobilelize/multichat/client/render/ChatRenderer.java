package net.mobilelize.multichat.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.mixin.client.ChatHudAccessor;

import java.util.ArrayList;
import java.util.List;

public class ChatRenderer {

    public static final ChatPanel LEFT  = new ChatPanel(ChatPanel.Anchor.LEFT,  0, true,  false);
    public static final ChatPanel RIGHT = new ChatPanel(ChatPanel.Anchor.RIGHT, 0, false, false);

    public static ChatTab contextTab = null;

    public static final List<ChatTab> TABS = new ArrayList<>(List.of(
        new ChatTab("All",     TabType.ALL,     TabVisibility.BOTH),
        new ChatTab("Vanilla", TabType.VANILLA, TabVisibility.LEFT)
    ));

    public static int lastMouseX = 0;

    public static final int TAB_HEIGHT = 10;
    public static final int ARROW_W    = 10;

    public static String getTabLabel(ChatPanel panel, ChatTab tab) {
        if (panel.showBadges && tab.showBadge && tab.unreadCount > 0)
            return tab.name + " [" + tab.unreadCount + "]";
        return tab.name;
    }

    public static void resetForRefresh() {
        LEFT.resetScrollPositions();
        RIGHT.resetScrollPositions();
        TABS.forEach(t -> {
            t.messages.clear();
            t.unreadCount = 0;
        });
    }

    public static void render(ChatHud chat, DrawContext context, int currentTick,
                               int mouseX, int mouseY, boolean focused, boolean skipLeftMessages) {
        ChatHudAccessor accessor = (ChatHudAccessor) chat;
        MinecraftClient client = accessor.getClient();

        lastMouseX = mouseX;

        if (client.options.getChatVisibility().getValue() == ChatVisibility.HIDDEN) return;

        boolean anyVisible = (LEFT.enabled  && (!LEFT.showOnlyWhenFocused  || focused))
                          || (RIGHT.enabled && (!RIGHT.showOnlyWhenFocused || focused));
        if (!anyVisible) return;

        client.getProfiler().push("multichat");

        float scale       = (float) chat.getChatScale();
        int chatWidth     = MathHelper.ceil((float) chat.getWidth() / scale);
        int windowHeight  = context.getScaledWindowHeight();
        int windowWidth   = context.getScaledWindowWidth();

        double chatOpacity   = (Double) client.options.getChatOpacity().getValue() * 0.9 + 0.1;
        double bgOpacity     = (Double) client.options.getTextBackgroundOpacity().getValue();
        double lineSpacing   = (Double) client.options.getChatLineSpacing().getValue();
        int lineHeight       = (int)(9.0 * (lineSpacing + 1.0));
        int textOffset       = (int) Math.round(-8.0 * (lineSpacing + 1.0) + 4.0 * lineSpacing);
        int visibleLineCount = chat.getVisibleLineCount();
        boolean hasUnread    = accessor.getHasUnreadNewMessages();
        int topY             = MathHelper.floor((float)(windowHeight - 40) / scale);

        for (ChatPanel panel : List.of(LEFT, RIGHT)) {
            if (!panel.enabled) continue;
            if (panel.showOnlyWhenFocused && !focused) continue;

            ChatTab activeTab = panel.getActiveTab();
            if (activeTab == null) continue;
            int xOffset = panel.computeXOffset(windowWidth, scale, chatWidth);

            if (focused) renderTabBar(client, context, panel, xOffset, topY, scale, chatWidth);

            if (activeTab.type == TabType.VANILLA) continue;
            if (panel == LEFT && skipLeftMessages) continue;

            List<ChatHudLine.Visible> msgs = activeTab.messages;
            if (msgs.isEmpty()) continue;

            int hoveredIndex = computeHoveredIndex(panel, client, mouseX, mouseY,
                    visibleLineCount, windowHeight, scale, lineHeight, chatWidth, xOffset, focused);

            renderAt(client, context, currentTick, focused,
                    msgs, msgs.size(), panel.getScrolledLines(activeTab), hasUnread,
                    chatWidth, topY, scale, chatOpacity, bgOpacity,
                    lineHeight, textOffset, visibleLineCount, hoveredIndex, xOffset);
        }

        client.getProfiler().pop();
    }

    private static void renderTabBar(MinecraftClient client, DrawContext context,
                                      ChatPanel panel, int xOffset, int topY, float scale, int chatWidth) {
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0F);
        context.getMatrices().translate(4.0F + xOffset, 0.0F, 0.0F);

        // Render space: x = -4 (left panel edge) to x = chatWidth + 4 (right panel edge)
        List<ChatTab> visibleTabs = panel.getVisibleTabs();
        panel.tabScrollOffset = MathHelper.clamp(panel.tabScrollOffset, 0, Math.max(0, visibleTabs.size() - 1));

        boolean hasLeft = panel.tabScrollOffset > 0;
        int tabsEnd = chatWidth + 4 - (hasLeft ? 0 : 0); // right edge, adjusted after we know right arrow

        // Determine if right arrow needed: simulate tab layout
        int simX = hasLeft ? -4 + ARROW_W : -4;
        int simEnd = chatWidth + 4 - ARROW_W; // reserve right arrow space for check
        boolean hasRight = false;
        for (int i = panel.tabScrollOffset; i < visibleTabs.size(); i++) {
            int tw = client.textRenderer.getWidth(getTabLabel(panel, visibleTabs.get(i))) + 8;
            if (simX + tw > simEnd) { hasRight = true; break; }
            simX += tw + 1;
        }

        int tabAreaEnd = chatWidth + 4 - (hasRight ? ARROW_W : 0);

        // Left arrow
        int x = -4;
        if (hasLeft) {
            context.fill(x, topY, x + ARROW_W, topY + TAB_HEIGHT, 0x88444444);
            context.drawTextWithShadow(client.textRenderer, "<", x + 2, topY + 1, 0xFFCCCCCC);
            x += ARROW_W;
        }

        // Tabs
        int lastRendered = panel.tabScrollOffset - 1;
        for (int i = panel.tabScrollOffset; i < visibleTabs.size(); i++) {
            ChatTab tab = visibleTabs.get(i);
            String label = getTabLabel(panel, tab);
            int tabWidth = client.textRenderer.getWidth(label) + 8;
            if (x + tabWidth > tabAreaEnd) break;

            boolean active = (i == panel.activeTab);
            context.fill(x, topY, x + tabWidth, topY + TAB_HEIGHT, active ? 0xBB888888 : 0x88444444);
            context.drawTextWithShadow(client.textRenderer, label, x + 4, topY + 1, active ? 0xFFFFFFFF : 0xFF999999);
            x += tabWidth + 1;
            lastRendered = i;
        }
        panel.tabLastRendered = lastRendered;

        // Partial next tab in the gap before the right arrow
        if (hasRight && lastRendered + 1 < visibleTabs.size()) {
            int remaining = tabAreaEnd - x;
            if (remaining > 4) {
                ChatTab nextTab = visibleTabs.get(lastRendered + 1);
                String label = getTabLabel(panel, nextTab);
                int tabWidth = client.textRenderer.getWidth(label) + 8;
                boolean active = (lastRendered + 1 == panel.activeTab);

                int sx1 = Math.round((x + 4 + xOffset) * scale);
                int sy1 = Math.round(topY * scale);
                int sx2 = Math.round((tabAreaEnd + 4 + xOffset) * scale);
                int sy2 = Math.round((topY + TAB_HEIGHT) * scale);

                context.enableScissor(sx1, sy1, sx2, sy2);
                context.fill(x, topY, x + tabWidth, topY + TAB_HEIGHT, active ? 0xBB888888 : 0x88444444);
                context.drawTextWithShadow(client.textRenderer, label, x + 4, topY + 1, active ? 0xFFFFFFFF : 0xFF999999);
                context.disableScissor();
            }
        }

        // Right arrow
        if (hasRight) {
            int ax = tabAreaEnd;
            context.fill(ax, topY, ax + ARROW_W, topY + TAB_HEIGHT, 0x88444444);
            context.drawTextWithShadow(client.textRenderer, ">", ax + 2, topY + 1, 0xFFCCCCCC);
        }

        context.getMatrices().pop();
    }

    private static void renderAt(MinecraftClient client, DrawContext context,
                                  int currentTick, boolean focused,
                                  List<ChatHudLine.Visible> visibleMessages, int totalMessages,
                                  int scrolledLines, boolean hasUnread,
                                  int chatWidth, int topY, float scale,
                                  double chatOpacity, double bgOpacity,
                                  int lineHeight, int textOffset, int visibleLineCount,
                                  int hoveredIndex, int xOffset) {
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0F);
        context.getMatrices().translate(4.0F + xOffset, 0.0F, 0.0F);

        int linesDrawn = 0;

        for (int r = 0; r + scrolledLines < totalMessages && r < visibleLineCount; r++) {
            int s = r + scrolledLines;
            ChatHudLine.Visible visible = visibleMessages.get(s);
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

            context.fill(-4, lineBot, chatWidth + 4 + 4, lineTop, bgAlpha << 24);

            MessageIndicator indicator = visible.indicator();
            if (indicator != null) {
                context.fill(-4, lineBot, -2, lineTop, indicator.indicatorColor() | (textAlpha << 24));
                if (s == hoveredIndex && indicator.icon() != null) {
                    int iconX = client.textRenderer.getWidth(visible.content()) + 4;
                    int iconY = textY + 9 - indicator.icon().height - 1;
                    indicator.icon().draw(context, iconX, iconY);
                }
            }

            context.getMatrices().push();
            context.getMatrices().translate(0.0F, 0.0F, 50.0F);
            context.drawTextWithShadow(client.textRenderer, visible.content(), 0, textY, 16777215 + (textAlpha << 24));
            context.getMatrices().pop();
        }

        long queueCount = client.getMessageHandler().getUnprocessedMessageCount();
        if (queueCount > 0L) {
            int queueTextAlpha = (int)(128.0 * chatOpacity);
            int queueBgAlpha   = (int)(255.0 * bgOpacity);
            context.getMatrices().push();
            context.getMatrices().translate(0.0F, (float) topY, 0.0F);
            context.fill(-2, 0, chatWidth + 4, 9, queueBgAlpha << 24);
            context.getMatrices().translate(0.0F, 0.0F, 50.0F);
            context.drawTextWithShadow(client.textRenderer, Text.translatable("chat.queue", queueCount),
                    0, 1, 16777215 + (queueTextAlpha << 24));
            context.getMatrices().pop();
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
                context.fill(scrollX,     -scrollPos, scrollX + 2, -scrollPos - thumbHeight, 100, barColor  + (alpha << 24));
                context.fill(scrollX + 2, -scrollPos, scrollX + 1, -scrollPos - thumbHeight, 100, 13421772 + (alpha << 24));
            }
        }

        context.getMatrices().pop();
    }

    public static int computeHoveredIndex(ChatPanel panel, MinecraftClient client,
                                           int mouseX, int mouseY, int visibleLineCount,
                                           int windowHeight, float scale, int lineHeight,
                                           int chatWidth, int xOffset, boolean focused) {
        if (!focused) return -1;

        double rawChatX   = mouseX / (double) scale - 4.0;
        double localChatX = rawChatX - xOffset;
        double chatY      = ((double) windowHeight - mouseY - 40.0) / (scale * lineHeight);

        if (localChatX < -4.0 || localChatX > chatWidth) return -1;

        ChatTab activeTab = panel.getActiveTab();
        if (activeTab == null) return -1;

        List<ChatHudLine.Visible> visibleMessages = activeTab.messages;
        int scrolledLines = panel.getScrolledLines(activeTab);
        int maxLines = Math.min(visibleLineCount, visibleMessages.size());

        if (chatY < 0.0 || chatY >= maxLines) return -1;

        int lineIdx = MathHelper.floor(chatY + scrolledLines);
        if (lineIdx < 0 || lineIdx >= visibleMessages.size()) return -1;

        while (lineIdx >= 0) {
            if (visibleMessages.get(lineIdx).endOfEntry()) return lineIdx;
            lineIdx--;
        }
        return -1;
    }

    private static double getOpacityMultiplier(int age) {
        double d = (double) age / 200.0;
        d = 1.0 - d;
        d *= 10.0;
        d = MathHelper.clamp(d, 0.0, 1.0);
        d *= d;
        return d;
    }
}
