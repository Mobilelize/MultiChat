package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.ContextMenu;
import net.mobilelize.multichat.client.MessageContextMenu;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
@Mixin(value = ChatHud.class, priority = 2000)
public class ChatHudMixin {

    @Final
    @Shadow
    private List<ChatHudLine> messages;

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
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        boolean leftVanilla = activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA;
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
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        if (activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA) {
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, focused, true);
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void resetPanelScroll(CallbackInfo ci) {
        for (ChatPanel panel : ChatManager.getAllPanels())
            panel.resetScrollPositionsExcept(TabType.NOTES);
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void scrollPanel(int scroll, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        boolean leftIsVanilla = activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA;
        if (!leftIsVanilla) ci.cancel();

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();
        double sf        = LzClient.mc().getWindow().getScaleFactor();
        double mouseY    = LzClient.mc().mouse.getY() / sf;

        for (ChatPanel panel : ChatManager.getAllPanels()) {
            if (!panel.isEnabled()) continue;
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
            List<LzChatLine> panelMsgs = panel.getTabLines(active);
            int max = Math.max(0, (panelMsgs != null ? panelMsgs.size() : 0) - panel.resolveLines());
            panel.setScrolledLines(active, LzMath.clamp(panel.getScrolledLines(active) + scroll, 0, max));
            return;
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Inject(method = "refresh", at = @At("HEAD"))
    private void onRefresh(CallbackInfo ci) {
        ChatManager.resetForRefresh();
    }

    // ── Route incoming messages ───────────────────────────────────────────────
    // MC-VERSION-SENSITIVE: addMessage descriptor. Hooks the public 3-arg overload only —
    // refresh() calls addVisibleMessage directly and never triggers these.

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD")
    )
    private void captureRaw(Text message, @Nullable MessageSignatureData sig, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        MessageRouter.pendingRaw = LzText.wrapMc(message);
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("RETURN")
    )
    private void routeOnReturn(Text message, @Nullable MessageSignatureData sig, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if (messages.isEmpty()) return;
        MessageRouter.routeMessages(messages.getFirst());
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

        ChatPanel mpI = ChatManager.primaryPanel();
        ChatTab leftActive  = mpI != null ? mpI.getActiveTab() : null;
        boolean leftIsVanilla = leftActive != null && mpI.isEnabled() && leftActive.type() == TabType.VANILLA;

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

    @Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
    private void suppressTextStyleWhenMenuOpen(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;
        if (ContextMenu.isOpen() || MessageContextMenu.isOpen()) { cir.setReturnValue(null); }
    }

    @Inject(method = "getTextStyleAt", at = @At("RETURN"), cancellable = true)
    private void getTextStyleAtAllPanels(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (MultichatClient.CONFIG.modDisabled) return;

        ChatPanel mpS = ChatManager.primaryPanel();
        ChatTab leftActive = mpS != null ? mpS.getActiveTab() : null;
        boolean leftIsVanilla = leftActive != null && mpS.isEnabled()
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
