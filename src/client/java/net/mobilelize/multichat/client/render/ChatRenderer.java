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
import net.mobilelize.multichat.mixin.client.ChatHudAccessor;

import java.util.ArrayList;
import java.util.List;

public class ChatRenderer {

    public static final List<ChatPanel> PANELS = new ArrayList<>(List.of(
        new ChatPanel(ChatPanel.Anchor.LEFT,  0),   // panel 0 — default left position
        new ChatPanel(ChatPanel.Anchor.RIGHT, 0)    // panel 1 — right wall
    ));

    public static int lastMouseX = 0;

    private static int routeCounter = 0;

    /** Returns the panel index this message should be routed to, then advances counter. */
    public static int nextRouteTarget() {
        int idx = routeCounter % PANELS.size();
        routeCounter++;
        return idx;
    }

    /** Called before refresh() re-routes all history — clears panels so no duplicates. */
    public static void resetForRefresh() {
        routeCounter = 0;
        PANELS.forEach(p -> p.messages.clear());
    }

    public static void render(ChatHud chat, DrawContext context, int currentTick,
                               int mouseX, int mouseY, boolean focused) {
        ChatHudAccessor accessor = (ChatHudAccessor) chat;
        MinecraftClient client = accessor.getClient();

        lastMouseX = mouseX;

        if (client.options.getChatVisibility().getValue() == ChatVisibility.HIDDEN) return;

        boolean anyMessages = PANELS.stream().anyMatch(p -> !p.messages.isEmpty());
        if (!anyMessages) return;

        client.getProfiler().push("chat");

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

        for (ChatPanel panel : PANELS) {
            List<ChatHudLine.Visible> panelMessages = panel.messages;
            if (panelMessages.isEmpty()) continue;

            int xOffset = panel.computeXOffset(windowWidth, scale, chatWidth);
            int hoveredIndex = computeHoveredIndex(panel, client, mouseX, mouseY,
                    visibleLineCount, windowHeight, scale, lineHeight, chatWidth, xOffset, focused);

            renderAt(client, context, currentTick, focused,
                    panelMessages, panelMessages.size(), panel.scrolledLines, hasUnread,
                    chatWidth, topY, scale, chatOpacity, bgOpacity,
                    lineHeight, textOffset, visibleLineCount, hoveredIndex, xOffset);
        }

        client.getProfiler().pop();
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

    /**
     * Returns the message index (endOfEntry line) the mouse is over for the given panel,
     * or -1 if not hovering this panel.
     */
    public static int computeHoveredIndex(ChatPanel panel, MinecraftClient client,
                                           int mouseX, int mouseY, int visibleLineCount,
                                           int windowHeight, float scale, int lineHeight,
                                           int chatWidth, int xOffset, boolean focused) {
        if (!focused) return -1;

        double rawChatX = mouseX / (double) scale - 4.0;
        double localChatX = rawChatX - xOffset;
        double chatY = ((double) windowHeight - mouseY - 40.0) / (scale * lineHeight);

        if (localChatX < -4.0 || localChatX > chatWidth) return -1;

        List<ChatHudLine.Visible> visibleMessages = panel.messages;
        int scrolledLines = panel.scrolledLines;
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
