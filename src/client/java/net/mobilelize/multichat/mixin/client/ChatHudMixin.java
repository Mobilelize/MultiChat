package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;
import net.mobilelize.multichat.integration.chatheads.ChatHeadsSupport;
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
    // MC-VERSION-SENSITIVE: descriptor must match exactly.
    // MC 1.21.11: TextRenderer added as 2nd param; focused→interactable; extra boolean bl (shouldInsert) at end.

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hookRenderHead(DrawContext context, TextRenderer textRenderer, int currentTick,
                                 int mouseX, int mouseY, boolean interactable, boolean bl,
                                 CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        boolean leftVanilla = activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA;
        if (!leftVanilla) {
            ci.cancel();
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, interactable, false);
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At("RETURN")
    )
    private void hookRenderReturn(DrawContext context, TextRenderer textRenderer, int currentTick,
                                   int mouseX, int mouseY, boolean interactable, boolean bl,
                                   CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        if (activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA) {
            ChatRenderer.render((ChatHud)(Object)this, LzDrawContext.wrap(context), currentTick, mouseX, mouseY, interactable, true);
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
        // Same instant chat_heads' own ChatListenerMixin has just set ChatHeads.lastSenderData
        // for this exact message (its @ModifyArg fires immediately before this vanilla method
        // runs) — see ChatHeadsSupport's BEFORE_LINE support section for the full trace.
        MessageRouter.pendingHeadRef = ChatHeadsSupport.captureLastSenderData();
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("RETURN")
    )
    private void routeOnReturn(Text message, @Nullable MessageSignatureData sig, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if (messages.isEmpty()) return;
        MessageRouter.routeMessages(messages.getFirst());
    }

    @Unique
    private static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }

}
