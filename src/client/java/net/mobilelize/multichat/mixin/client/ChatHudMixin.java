package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Style;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.ContextMenu;
import net.mobilelize.multichat.client.MessageContextMenu;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    // ------------------------------------------------------------------
    // Render — HEAD: cancel vanilla and render ourselves unless VANILLA tab
    // ------------------------------------------------------------------

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hookRenderHead(DrawContext context, int currentTick, int mouseX, int mouseY,
                                 boolean focused, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        boolean leftVanilla = activeLeft != null && ChatRenderer.LEFT.enabled && activeLeft.type == TabType.VANILLA;
        if (!leftVanilla) {
            ci.cancel();
            ChatRenderer.render((ChatHud)(Object)this, context, currentTick, mouseX, mouseY, focused, false);
        }
    }

    // ------------------------------------------------------------------
    // Render — RETURN: fires only when vanilla ran (VANILLA tab active on left)
    // ------------------------------------------------------------------

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
        at = @At("RETURN")
    )
    private void hookRenderReturn(DrawContext context, int currentTick, int mouseX, int mouseY,
                                   boolean focused, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        if (activeLeft != null && ChatRenderer.LEFT.enabled && activeLeft.type == TabType.VANILLA) {
            ChatRenderer.render((ChatHud)(Object)this, context, currentTick, mouseX, mouseY, focused, true);
        }
    }

    // ------------------------------------------------------------------
    // Scroll
    // ------------------------------------------------------------------

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void resetPanelScroll(CallbackInfo ci) {
        ChatRenderer.LEFT.resetScrollPositions();
        ChatRenderer.RIGHT.resetScrollPositions();
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void scrollPanel(int scroll, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        boolean leftIsVanilla = activeLeft != null && ChatRenderer.LEFT.enabled && activeLeft.type == TabType.VANILLA;
        if (!leftIsVanilla) ci.cancel();

        ChatHud self        = (ChatHud)(Object)this;
        ChatHudAccessor acc = (ChatHudAccessor) self;

        float scale     = (float) LzChat.chatScale();
        int chatWidth   = LzMath.ceil((float) LzChat.chatWidth() / scale);
        int windowWidth = LzClient.scaledWindowWidth();

        double rawChatX = ChatRenderer.lastMouseX / (double) scale - 4.0;

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA) continue;
            if (active.messages.isEmpty()) continue;

            int xOffset       = panel.computeXOffset(windowWidth, scale, chatWidth);
            double localChatX = rawChatX - xOffset;

            if (localChatX < -4.0 || localChatX > chatWidth + 8) continue;

            ci.cancel(); // cancel vanilla scroll only when we're handling it
            int max = Math.max(0, active.messages.size() - LzChat.visibleLineCount());
            panel.setScrolledLines(active, LzMath.clamp(panel.getScrolledLines(active) + scroll, 0, max));
            return;
        }
    }

    // ------------------------------------------------------------------
    // Refresh
    // ------------------------------------------------------------------

    @Inject(method = "refresh", at = @At("HEAD"))
    private void onRefresh(CallbackInfo ci) {
        ChatRenderer.resetForRefresh();
    }

    // ------------------------------------------------------------------
    // Route incoming messages globally
    // ------------------------------------------------------------------

    @Inject(method = "addVisibleMessage", at = @At("HEAD"))
    private void routeGlobally(ChatHudLine message, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;

        float scale  = (float) LzChat.chatScale();
        int baseWidth = LzMath.floor((double) LzChat.chatWidth() / scale);
        int width = baseWidth - getIndicatorWidth(LzMessageIndicator.wrapNullable(message.indicator()));

        List<LzOrderedText> lines = LzChat.breakLines(LzText.wrapMc(message.content()), width);

        MessageRouter.routeLines(message, lines);
    }

    // ------------------------------------------------------------------
    // Indicator hover tooltip — use our panels instead of vanilla messages
    // ------------------------------------------------------------------

    @Inject(method = "getIndicatorAt", at = @At("HEAD"), cancellable = true)
    private void getIndicatorAt(double mouseX, double mouseY,
                                 CallbackInfoReturnable<@Nullable MessageIndicator> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (ContextMenu.isOpen() || MessageContextMenu.isOpen()) { cir.setReturnValue(null); return; }

        ChatTab leftActive = ChatRenderer.LEFT.getActiveTab();
        if (leftActive != null && ChatRenderer.LEFT.enabled && leftActive.type == TabType.VANILLA) return;
        if (!MultichatClient.CONFIG.showIndicators) { cir.setReturnValue(null); return; }

        float scale          = (float) LzChat.chatScale();
        int chatWidth        = LzMath.ceil((float) LzChat.chatWidth() / scale);
        int windowWidth      = LzClient.scaledWindowWidth();
        int windowHeight     = LzClient.scaledWindowHeight();
        int lineHeight       = LzChat.chatLineHeight();
        int visibleLineCount = LzChat.visibleLineCount();

        // toChatLineY — same for all panels
        double chatLineY = ((double) windowHeight - mouseY - 40.0) / (scale * lineHeight);

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA || active.messages.isEmpty()) continue;

            int xOffset = panel.computeXOffset(windowWidth, scale, chatWidth);

            // toChatLineX — panel-local
            double chatLineX = mouseX / scale - 4.0 - xOffset;
            if (chatLineX < -4.0) continue; // outside this panel's left edge

            // getMessageLineIndex equivalent (Y bounds)
            int maxLines = Math.min(visibleLineCount, active.messages.size());
            if (chatLineY < 0.0 || chatLineY >= maxLines) continue;
            int lineIdx = LzMath.floor(chatLineY + panel.getScrolledLines(active));
            if (lineIdx < 0 || lineIdx >= active.messages.size()) continue;

            // getMessageIndex: walk back to endOfEntry
            while (lineIdx >= 0 && !active.messages.get(lineIdx).endOfEntry()) lineIdx--;
            if (lineIdx < 0) continue;

            LzChatLine line = active.messages.get(lineIdx);
            LzMessageIndicator indicator = line.indicator();
            if (indicator == null) { cir.setReturnValue(null); return; }

            // isXInsideIndicatorIcon equivalent
            if (chatLineX < 0.0) { cir.setReturnValue(indicator.mc()); return; } // over color strip
            if (!indicator.hasIcon()) { cir.setReturnValue(null); return; }
            int indicatorX = LzDraw.textWidth(line.content()) + 4; // getIndicatorX equivalent
            int iconW = indicator.mc().icon() != null ? indicator.mc().icon().width : 0;
            cir.setReturnValue(chatLineX >= indicatorX && chatLineX <= indicatorX + iconW ? indicator.mc() : null);
            return;
        }

        cir.setReturnValue(null);
    }

    @Unique
    private static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }

    // ------------------------------------------------------------------
    // Click / style hit-test for all panels
    // ------------------------------------------------------------------

    @Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
    private void suppressTextStyleWhenMenuOpen(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (ContextMenu.isOpen() || MessageContextMenu.isOpen()) { cir.setReturnValue(null); }
    }

    @Inject(method = "getTextStyleAt", at = @At("RETURN"), cancellable = true)
    private void getTextStyleAtAllPanels(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab leftActive = ChatRenderer.LEFT.getActiveTab();
        boolean leftIsVanilla = leftActive != null && ChatRenderer.LEFT.enabled
            && leftActive.type == TabType.VANILLA;
        if (cir.getReturnValue() != null && leftIsVanilla) return;

        float scale      = (float) LzChat.chatScale();
        int chatWidth    = LzMath.ceil((float) LzChat.chatWidth() / scale);
        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();

        int lineHeight = LzChat.chatLineHeight();
        double chatY   = ((double) windowHeight - y - 40.0) / (scale * lineHeight);
        double rawChatX    = x / scale - 4.0;

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;

            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA) continue;
            if (active.messages.isEmpty()) continue;

            int xOffset       = panel.computeXOffset(windowWidth, scale, chatWidth);
            double localChatX = rawChatX - xOffset;

            if (localChatX < -4.0 || localChatX > chatWidth) continue;

            int scrolledLines = panel.getScrolledLines(active);
            int maxLines = Math.min(LzChat.visibleLineCount(), active.messages.size());
            if (chatY < 0.0 || chatY >= maxLines) continue;

            int lineIdx = LzMath.floor(chatY + scrolledLines);
            if (lineIdx < 0 || lineIdx >= active.messages.size()) continue;

            Style style = LzDraw.textHandler()
                    .getStyleAt(active.messages.get(lineIdx).content().mc(), LzMath.floor(localChatX));
            if (style != null) {
                cir.setReturnValue(style);
                return;
            }
        }
    }
}
