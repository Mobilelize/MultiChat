package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.text.Style;
import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.render.LzDrawContext;
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
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, focused, false);
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
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, focused, true);
        }
    }

    // ------------------------------------------------------------------
    // Scroll
    // ------------------------------------------------------------------

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void resetPanelScroll(CallbackInfo ci) {
        ChatRenderer.PANELS.forEach(ChatPanel::resetScrollPositions);
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void scrollPanel(int scroll, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        boolean leftIsVanilla = activeLeft != null && ChatRenderer.LEFT.enabled && activeLeft.type == TabType.VANILLA;
        if (!leftIsVanilla) ci.cancel();

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();
        double sf        = LzClient.mc().getWindow().getScaleFactor();
        double mouseY    = LzClient.mc().mouse.getY() / sf;

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA) continue;
            if (active.messages.isEmpty()) continue;

            float scale      = panel.resolveScale();
            int   chatWidth  = panel.resolveWidth();
            int   lines      = panel.resolveLines();
            int   lineHeight = panel.resolveLineHeight();
            int   topY       = panel.resolveTopY(windowHeight);
            int   xOffset    = panel.computeXOffset(windowWidth);

            double rawChatX   = ChatRenderer.lastMouseX / (double) scale - 4.0;
            double localChatX = rawChatX - xOffset;
            if (localChatX < -4.0 || localChatX > chatWidth + 8) continue;

            double panelTop    = (topY - lines * lineHeight) * (double) scale;
            double panelBottom = (topY + ChatRenderer.TAB_HEIGHT) * (double) scale;
            if (mouseY < panelTop || mouseY > panelBottom) continue;

            ci.cancel();
            List<LzChatLine> panelMsgs = ChatRenderer.getMessages(panel, active);
            int max = Math.max(0, panelMsgs.size() - panel.resolveLines());
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

        MessageRouter.routeLines(
            message.creationTick(),
            LzText.wrapMc(message.content()),
            LzMessageIndicator.wrapNullable(message.indicator()),
            lines
        );
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
        boolean leftIsVanilla = leftActive != null && ChatRenderer.LEFT.enabled && leftActive.type == TabType.VANILLA;
        if (!MultichatClient.CONFIG.showIndicators) { if (!leftIsVanilla) cir.setReturnValue(null); return; }

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA || active.messages.isEmpty()) continue;

            float scale          = panel.resolveScale();
            int chatWidth        = panel.resolveWidth();
            int lineHeight       = panel.resolveLineHeight();
            int visibleLineCount = panel.resolveLines();

            int xOffset      = panel.computeXOffset(windowWidth);
            int topY         = panel.resolveTopY(windowHeight);
            double chatLineY = (topY * (double) scale - mouseY) / (scale * lineHeight);
            double chatLineX = mouseX / scale - 4.0 - xOffset;
            if (chatLineX < -4.0 || chatLineX > chatWidth) continue;

            List<LzChatLine> panelMsgs = ChatRenderer.getMessages(panel, active);
            int maxLines = Math.min(visibleLineCount, panelMsgs.size());
            if (chatLineY < 0.0 || chatLineY >= maxLines) continue;
            int lineIdx = LzMath.floor(chatLineY + panel.getScrolledLines(active));
            if (lineIdx < 0 || lineIdx >= panelMsgs.size()) continue;

            // getMessageIndex: walk back to endOfEntry
            while (lineIdx >= 0 && !panelMsgs.get(lineIdx).endOfEntry()) lineIdx--;
            if (lineIdx < 0) continue;

            LzChatLine line = panelMsgs.get(lineIdx);
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

        if (!leftIsVanilla) cir.setReturnValue(null);
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

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;

            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA || active.messages.isEmpty()) continue;

            float scale    = panel.resolveScale();
            int chatWidth  = panel.resolveWidth();
            int lineHeight = panel.resolveLineHeight();

            int panelTopY     = panel.resolveTopY(windowHeight);
            double chatY      = (panelTopY * (double) scale - y) / (scale * lineHeight);
            double rawChatX   = x / scale - 4.0;
            int xOffset       = panel.computeXOffset(windowWidth);
            double localChatX = rawChatX - xOffset;

            if (localChatX < -4.0 || localChatX > chatWidth) continue;

            int scrolledLines = panel.getScrolledLines(active);
            List<LzChatLine> panelMsgs = ChatRenderer.getMessages(panel, active);
            int maxLines = Math.min(panel.resolveLines(), panelMsgs.size());
            if (chatY < 0.0 || chatY >= maxLines) continue;

            int lineIdx = LzMath.floor(chatY + scrolledLines);
            if (lineIdx < 0 || lineIdx >= panelMsgs.size()) continue;

            Style style = LzDraw.textHandler()
                    .getStyleAt(panelMsgs.get(lineIdx).content().mc(), LzMath.floor(localChatX));
            if (style != null) {
                cir.setReturnValue(style);
                return;
            }
        }

        // Suppress vanilla only when left panel is non-vanilla (empty tab or no clickable text found)
        if (!leftIsVanilla) cir.setReturnValue(null);
    }
}
