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

    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hookRender(DrawContext context, int currentTick, int mouseX, int mouseY,
                             boolean focused, CallbackInfo ci) {
        ci.cancel();
        ChatRenderer.render((ChatHud)(Object)this, context, currentTick, mouseX, mouseY, focused);
    }

    @Inject(method = "resetScroll", at = @At("HEAD"))
    private void resetPanelScroll(CallbackInfo ci) {
        ChatRenderer.PANELS.forEach(p -> p.scrolledLines = 0);
    }

    @Inject(method = "scroll", at = @At("HEAD"), cancellable = true)
    private void scrollPanel(int scroll, CallbackInfo ci) {
        ci.cancel();

        ChatHud self       = (ChatHud)(Object)this;
        ChatHudAccessor acc = (ChatHudAccessor) self;
        MinecraftClient client = acc.getClient();

        float scale    = (float) self.getChatScale();
        int chatWidth  = MathHelper.ceil((float) self.getWidth() / scale);
        int windowWidth = client.getWindow().getScaledWidth();

        double rawChatX = ChatRenderer.lastMouseX / (double) scale - 4.0;

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;
            if (panel.messages.isEmpty()) continue;

            int xOffset       = panel.computeXOffset(windowWidth, scale, chatWidth);
            double localChatX = rawChatX - xOffset;

            if (localChatX < -4.0 || localChatX > chatWidth) continue;

            int max = Math.max(0, panel.messages.size() - self.getVisibleLineCount());
            panel.scrolledLines = MathHelper.clamp(panel.scrolledLines + scroll, 0, max);
            return;
        }
    }

    @Inject(method = "refresh", at = @At("HEAD"))
    private void onRefresh(CallbackInfo ci) {
        ChatRenderer.resetForRefresh();
    }

    /**
     * Intercepts every visible-line addition and routes it to the next panel
     * round-robin instead of vanilla's shared visibleMessages list.
     */
    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void routeToPanel(ChatHudLine message, CallbackInfo ci) {
        ci.cancel();

        ChatHud self = (ChatHud)(Object)this;
        ChatHudAccessor accessor = (ChatHudAccessor) self;
        MinecraftClient client = accessor.getClient();

        int baseWidth = MathHelper.floor((double) self.getWidth() / self.getChatScale());
        MessageIndicator.Icon icon = message.getIcon();
        int iconDeduction = icon != null ? icon.width + 4 + 2 : 0;
        int width = baseWidth - iconDeduction;

        List<OrderedText> lines = ChatMessages.breakRenderedChatMessageLines(
                message.content(), width, client.textRenderer);

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;

            for (int j = 0; j < lines.size(); j++) {
                boolean endOfEntry = j == lines.size() - 1;
                panel.messages.add(0, new ChatHudLine.Visible(
                        message.creationTick(), lines.get(j), message.indicator(), endOfEntry));
            }

            while (panel.messages.size() > 100) {
                panel.messages.remove(panel.messages.size() - 1);
            }
        }
    }

    /**
     * Extends vanilla getTextStyleAt to cover all registered panels.
     * Vanilla already handles panel 0 at default position; we handle the rest.
     * If panel 0 was moved (non-zero xOffset), vanilla miss is caught by the loop below.
     */
    @Inject(method = "getTextStyleAt", at = @At("RETURN"), cancellable = true)
    private void getTextStyleAtAllPanels(double x, double y, CallbackInfoReturnable<@Nullable Style> cir) {
        if (cir.getReturnValue() != null) return;

        ChatHud self        = (ChatHud)(Object)this;
        ChatHudAccessor acc = (ChatHudAccessor) self;
        MinecraftClient client = acc.getClient();

        float scale    = (float) self.getChatScale();
        int chatWidth  = MathHelper.ceil((float) self.getWidth() / scale);
        int windowWidth  = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();

        double lineSpacing = (Double) client.options.getChatLineSpacing().getValue();
        int lineHeight = (int)(9.0 * (lineSpacing + 1.0));
        double chatY = ((double) windowHeight - y - 40.0) / (scale * lineHeight);

        double rawChatX = x / scale - 4.0;

        for (int i = 0; i < ChatRenderer.PANELS.size(); i++) {
            ChatPanel panel   = ChatRenderer.PANELS.get(i);
            if (!panel.enabled) continue;
            if (panel.messages.isEmpty()) continue;

            int xOffset       = panel.computeXOffset(windowWidth, scale, chatWidth);
            double localChatX = rawChatX - xOffset;

            if (localChatX < -4.0 || localChatX > chatWidth) continue;

            int maxLines = Math.min(self.getVisibleLineCount(), panel.messages.size());
            if (chatY < 0.0 || chatY >= maxLines) continue;

            int lineIdx = MathHelper.floor(chatY + panel.scrolledLines);
            if (lineIdx < 0 || lineIdx >= panel.messages.size()) continue;

            Style style = client.textRenderer.getTextHandler()
                    .getStyleAt(panel.messages.get(lineIdx).content(), MathHelper.floor(localChatX));
            if (style != null) {
                cir.setReturnValue(style);
                return;
            }
        }
    }
}
