package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.render.LzDrawContext;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;
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
@Mixin(value = ChatComponent.class, priority = 2000)
public class ChatHudMixin {

    @Final
    @Shadow
    private List<GuiMessage> allMessages;

    // ── Render ────────────────────────────────────────────────────────────────
    // MC-VERSION-SENSITIVE: descriptor must match exactly.
    // MC 26.1: render(...) renamed extractRenderState(...); focused bool replaced by
    // ChatComponent.DisplayMode enum (displayMode.foreground == old interactable/focused).

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hookRenderHead(GuiGraphicsExtractor graphics, Font font, int currentTick,
                                 int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions,
                                 CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        boolean leftVanilla = activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA;
        if (!leftVanilla) {
            ci.cancel();
            ChatRenderer.render((ChatComponent)(Object)this, LzDrawContext.wrap(graphics), currentTick, mouseX, mouseY, displayMode.foreground, false);
        }
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At("RETURN")
    )
    private void hookRenderReturn(GuiGraphicsExtractor graphics, Font font, int currentTick,
                                   int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions,
                                   CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        if (activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA) {
            ChatRenderer.render((ChatComponent)(Object)this, LzDrawContext.wrap(graphics), currentTick, mouseX, mouseY, displayMode.foreground, true);
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────────
    // MC 26.1: resetScroll -> resetChatScroll, scroll -> scrollChat.

    @Inject(method = "resetChatScroll", at = @At("HEAD"))
    private void resetPanelScroll(CallbackInfo ci) {
        for (ChatPanel panel : ChatManager.getAllPanels())
            panel.resetScrollPositionsExcept(TabType.NOTES);
    }

    @Inject(method = "scrollChat", at = @At("HEAD"), cancellable = true)
    private void scrollPanel(int scroll, CallbackInfo ci) {
        if (MultichatClient.CONFIG.modDisabled) return;
        ChatPanel mp = ChatManager.primaryPanel();
        ChatTab activeLeft = mp != null ? mp.getActiveTab() : null;
        boolean leftIsVanilla = activeLeft != null && mp.isEnabled() && activeLeft.type() == TabType.VANILLA;
        if (!leftIsVanilla) ci.cancel();

        int windowWidth  = LzClient.scaledWindowWidth();
        int windowHeight = LzClient.scaledWindowHeight();
        double sf        = LzClient.mc().getWindow().getGuiScale();
        double mouseY    = LzClient.mc().mouseHandler.ypos() / sf;

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
    // MC 26.1: refresh -> rescaleChat.

    @Inject(method = "rescaleChat", at = @At("HEAD"))
    private void onRefresh(CallbackInfo ci) {
        ChatManager.resetForRefresh();
    }

    // ── Route incoming messages ───────────────────────────────────────────────
    // MC 26.1: the only remaining addMessage overload is the private 4-arg one;
    // addClientSystemMessage/addServerSystemMessage/addPlayerMessage all funnel into it.

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD")
    )
    private void captureRaw(Component message, @Nullable MessageSignature sig, GuiMessageSource source, @Nullable GuiMessageTag tag, CallbackInfo ci) {
        MessageRouter.pendingRaw = LzText.wrapMc(message);
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("RETURN")
    )
    private void routeOnReturn(Component message, @Nullable MessageSignature sig, GuiMessageSource source, @Nullable GuiMessageTag tag, CallbackInfo ci) {
        if (allMessages.isEmpty()) return;
        MessageRouter.routeMessages(allMessages.getFirst());
    }

    @Unique
    private static int getIndicatorWidth(@Nullable LzMessageIndicator indicator) {
        return indicator != null && indicator.hasIcon() ? indicator.iconWidth() + 6 : 0;
    }

}
