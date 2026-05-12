package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.util.ChatMessages;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.util.math.MathHelper;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
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
        ChatTab activeLeft = ChatRenderer.LEFT.getActiveTab();
        if (activeLeft != null && ChatRenderer.LEFT.enabled && activeLeft.type == TabType.VANILLA) return;

        ci.cancel();

        ChatHud self        = (ChatHud)(Object)this;
        ChatHudAccessor acc = (ChatHudAccessor) self;
        MinecraftClient client = acc.getClient();

        float scale     = (float) self.getChatScale();
        int chatWidth   = MathHelper.ceil((float) self.getWidth() / scale);
        int windowWidth = client.getWindow().getScaledWidth();

        double rawChatX = ChatRenderer.lastMouseX / (double) scale - 4.0;

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;
            ChatTab active = panel.getActiveTab();
            if (active == null || active.type == TabType.VANILLA) continue;
            if (active.messages.isEmpty()) continue;

            int xOffset       = panel.computeXOffset(windowWidth, scale, chatWidth);
            double localChatX = rawChatX - xOffset;

            if (localChatX < -4.0 || localChatX > chatWidth) continue;

            int max = Math.max(0, active.messages.size() - self.getVisibleLineCount());
            panel.setScrolledLines(active, MathHelper.clamp(panel.getScrolledLines(active) + scroll, 0, max));
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
        ChatHud self       = (ChatHud)(Object)this;
        ChatHudAccessor accessor = (ChatHudAccessor) self;
        MinecraftClient client = accessor.getClient();

        int baseWidth = MathHelper.floor((double) self.getWidth() / self.getChatScale());
        MessageIndicator indicator = message.indicator();
        int width = baseWidth - (indicator != null && indicator.icon() != null ? indicator.icon().width + 4 + 2 : 0);

        List<OrderedText> lines = ChatMessages.breakRenderedChatMessageLines(
                message.content(), width, client.textRenderer);

        MessageRouter.routeLines(message, lines);
    }

    // ------------------------------------------------------------------
    // Click / style hit-test for all panels
    // ------------------------------------------------------------------

    @Inject(method = "getTextStyleAt", at = @At("RETURN"), cancellable = true)
    private void getTextStyleAtAllPanels(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (cir.getReturnValue() != null) return;

        ChatHud self        = (ChatHud)(Object)this;
        ChatHudAccessor acc = (ChatHudAccessor) self;
        MinecraftClient client = acc.getClient();

        float scale      = (float) self.getChatScale();
        int chatWidth    = MathHelper.ceil((float) self.getWidth() / scale);
        int windowWidth  = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();

        double lineSpacing = (Double) client.options.getChatLineSpacing().getValue();
        int lineHeight     = (int)(9.0 * (lineSpacing + 1.0));
        double chatY       = ((double) windowHeight - y - 40.0) / (scale * lineHeight);
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
            int maxLines = Math.min(self.getVisibleLineCount(), active.messages.size());
            if (chatY < 0.0 || chatY >= maxLines) continue;

            int lineIdx = MathHelper.floor(chatY + scrolledLines);
            if (lineIdx < 0 || lineIdx >= active.messages.size()) continue;

            Style style = client.textRenderer.getTextHandler()
                    .getStyleAt(active.messages.get(lineIdx).content(), MathHelper.floor(localChatX));
            if (style != null) {
                cir.setReturnValue(style);
                return;
            }
        }
    }
}
