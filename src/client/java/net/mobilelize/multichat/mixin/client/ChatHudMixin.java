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
import net.mobilelize.multichat.client.ping.PingPlayer;
import net.mobilelize.multichat.client.render.ChatRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// =============================================================================
// MC-VERSION-SENSITIVE FILE
// When upgrading Minecraft, check every @Inject descriptor below.
// All rendering and hit-test logic has been moved to ChatRenderer so only the
// hook descriptors and thin glue need updating here.
// =============================================================================
@Mixin(ChatHud.class)
public class ChatHudMixin {

    // ── Render ────────────────────────────────────────────────────────────────
    // MC-VERSION-SENSITIVE: descriptor "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V"
    // New MC adds TextRenderer + extra boolean → update to:
    //   "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V"
    // and update callback params: (DrawContext, TextRenderer, int, int, int, boolean interactable, boolean bl)
    // The `focused` param is renamed `interactable`; `bl` is new.

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hookRenderHead(DrawContext context, int currentTick, int mouseX, int mouseY,
                                 boolean focused, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        boolean leftVanilla = activeLeft != null && ChatRenderer.LEFT.enabled() && activeLeft.type() == TabType.VANILLA;
        if (!leftVanilla) {
            ci.cancel();
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, focused, false);
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
        at = @At("RETURN")
    )
    private void hookRenderReturn(DrawContext context, int currentTick, int mouseX, int mouseY,
                                   boolean focused, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        if (activeLeft != null && ChatRenderer.LEFT.enabled() && activeLeft.type() == TabType.VANILLA) {
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, focused, true);
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void resetPanelScroll(CallbackInfo ci) {
        ChatRenderer.PANELS.forEach(ChatPanel::resetScrollPositions);
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void scrollPanel(int scroll, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        boolean leftIsVanilla = activeLeft != null && ChatRenderer.LEFT.enabled() && activeLeft.type() == TabType.VANILLA;
        if (!leftIsVanilla) ci.cancel();

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();
        double sf        = LzClient.mc().getWindow().getScaleFactor();
        double mouseY    = LzClient.mc().mouse.getY() / sf;

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled()) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type() == TabType.VANILLA) continue;
            if (!active.hasMessages()) continue;

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

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Inject(method = "refresh", at = @At("HEAD"))
    private void onRefresh(CallbackInfo ci) {
        ChatRenderer.resetForRefresh();
    }

    @Inject(method = "refresh", at = @At("RETURN"))
    private void onRefreshDone(CallbackInfo ci) {
        PingPlayer.suppressPingsUntil = 0L;
    }

    // ── Route incoming messages ───────────────────────────────────────────────

    @Inject(method = "addVisibleMessage", at = @At("HEAD"))
    private void routeGlobally(ChatHudLine message, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;

        float scale   = (float) LzChat.chatScale();
        int baseWidth = LzMath.floor((double) LzChat.chatWidth() / scale);
        int width     = baseWidth - getIndicatorWidth(LzMessageIndicator.wrapNullable(message.indicator()));

        List<LzOrderedText> lines = LzChat.breakLines(LzText.wrapMc(message.content()), width);

        MessageRouter.routeLines(
            message.creationTick(),
            LzText.wrapMc(message.content()),
            LzMessageIndicator.wrapNullable(message.indicator()),
            lines
        );
    }

    // ── Indicator hit-test ────────────────────────────────────────────────────
    // MC-VERSION-SENSITIVE: getIndicatorAt may be removed in newer MC.
    // Logic lives in ChatRenderer.resolveIndicatorAt — only the hook changes.
    // If removed: find the replacement (likely DrawnTextConsumer/Interactable callback)
    // and wire ChatRenderer.resolveIndicatorAt into that instead.

    @Inject(method = "getIndicatorAt", at = @At("HEAD"), cancellable = true)
    private void getIndicatorAt(double mouseX, double mouseY,
                                 CallbackInfoReturnable<@Nullable MessageIndicator> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (ContextMenu.isOpen() || MessageContextMenu.isOpen()) { cir.setReturnValue(null); return; }

        ChatTab leftActive  = ChatRenderer.LEFT.getActiveTab();
        boolean leftIsVanilla = leftActive != null && ChatRenderer.LEFT.enabled() && leftActive.type() == TabType.VANILLA;

        if (!MultichatClient.CONFIG.showIndicators) {
            if (!leftIsVanilla) cir.setReturnValue(null);
            return;
        }

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();

        ChatRenderer.IndicatorResult result = ChatRenderer.resolveIndicatorAt(mouseX, mouseY, windowWidth, windowHeight);
        if (result.found()) {
            cir.setReturnValue(result.indicator() != null ? result.indicator().mc() : null);
        } else if (!leftIsVanilla) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }

    // ── Text style hit-test ───────────────────────────────────────────────────
    // MC-VERSION-SENSITIVE: getTextStyleAt may be removed in newer MC.
    // Logic lives in ChatRenderer.resolveStyleAt — only the hook changes.
    // If removed: find the replacement (likely Interactable.text() Consumer<Style> callback)
    // and wire ChatRenderer.resolveStyleAt into that instead.
    // For cursor changes: implement ChatRenderer.updateCursor (currently a no-op).

    @Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
    private void suppressTextStyleWhenMenuOpen(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (ContextMenu.isOpen() || MessageContextMenu.isOpen()) { cir.setReturnValue(null); }
    }

    @Inject(method = "getTextStyleAt", at = @At("RETURN"), cancellable = true)
    private void getTextStyleAtAllPanels(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;

        ChatTab leftActive = ChatRenderer.LEFT.getActiveTab();
        boolean leftIsVanilla = leftActive != null && ChatRenderer.LEFT.enabled()
            && leftActive.type() == TabType.VANILLA;

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();

        Style style = ChatRenderer.resolveStyleAt(x, y, windowWidth, windowHeight);
        if (style != null) {
            cir.setReturnValue(style);
        } else if (!leftIsVanilla) {
            cir.setReturnValue(null);
        }
    }
}
