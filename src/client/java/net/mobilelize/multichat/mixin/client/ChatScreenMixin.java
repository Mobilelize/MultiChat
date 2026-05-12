package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.ContextMenu;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    // ------------------------------------------------------------------
    // Render — status bar + context menu overlay
    // ------------------------------------------------------------------

    @Shadow public abstract String normalize(String chatText);

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"))
    private void renderOverlays(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        ChatTab ctxTab = ChatRenderer.contextTab;
        if (ctxTab != null && !ctxTab.contextPrefix.isEmpty()) {
            String status = "▶ Tab: " + ctxTab.name + " [" + ctxTab.contextPrefix + "]";
            int screenH = context.getScaledWindowHeight();
            context.drawTextWithShadow(client.textRenderer, status, 4, screenH - 26, 0xDEDEDE);
        }

        ContextMenu.render(context, client, mouseX, mouseY);
    }

    @Inject(method = "removed()V", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ContextMenu.close();
    }

    // ------------------------------------------------------------------
    // Mouse click — tabs (left), context menu open (right), menu dismiss
    // ------------------------------------------------------------------

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button,
                                 CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Left-click: check if context menu should consume it
        if (button == 0 && ContextMenu.isOpen()) {
            boolean consumed = ContextMenu.handleClick((int) mouseX, (int) mouseY, client);
            cir.setReturnValue(consumed);
            return;
        }

        var chat     = client.inGameHud.getChatHud();
        float scale  = (float) chat.getChatScale();
        int chatWidth   = MathHelper.ceil((float) chat.getWidth() / scale);
        int windowHeight = client.getWindow().getScaledHeight();
        int windowWidth  = client.getWindow().getScaledWidth();
        int topY = MathHelper.floor((float)(windowHeight - 40) / scale);

        double tabBarScreenTop = topY * (double) scale;
        double tabBarScreenBot = (topY + ChatRenderer.TAB_HEIGHT) * (double) scale;

        if (mouseY < tabBarScreenTop || mouseY > tabBarScreenBot) return;

        for (ChatPanel panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (!panel.enabled) continue;

            int xOffset = panel.computeXOffset(windowWidth, scale, chatWidth);
            List<ChatTab> visibleTabs = panel.getVisibleTabs();

            boolean hasLeft  = panel.tabScrollOffset > 0;
            boolean hasRight = panel.tabLastRendered >= 0 && panel.tabLastRendered < visibleTabs.size() - 1;

            double panelLeftScreen  = xOffset * (double) scale;
            double panelRightScreen = (chatWidth + 8 + xOffset) * (double) scale;

            if (mouseX < panelLeftScreen || mouseX >= panelRightScreen) continue;

            // Arrow clicks (left-click only)
            if (button == 0) {
                if (hasLeft) {
                    double leftArrowEnd = (ChatRenderer.ARROW_W + xOffset) * (double) scale;
                    if (mouseX < leftArrowEnd) {
                        panel.tabScrollOffset = Math.max(0, panel.tabScrollOffset - 1);
                        cir.setReturnValue(true);
                        return;
                    }
                }
                if (hasRight) {
                    double rightArrowStart = (chatWidth + 4 - ChatRenderer.ARROW_W + 4 + xOffset) * (double) scale;
                    if (mouseX >= rightArrowStart) {
                        panel.tabScrollOffset++;
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }

            // Tab hit-test (shared for left and right click)
            int renderX    = hasLeft ? ChatRenderer.ARROW_W - 4 : -4;
            int tabAreaEnd = chatWidth + 4 - (hasRight ? ChatRenderer.ARROW_W : 0);

            for (int i = panel.tabScrollOffset; i < visibleTabs.size(); i++) {
                ChatTab tab = visibleTabs.get(i);
                String label = ChatRenderer.getTabLabel(panel, tab);
                int tabWidth = client.textRenderer.getWidth(label) + 8;

                boolean fits = renderX + tabWidth <= tabAreaEnd;
                double screenStart = (renderX + 4 + xOffset) * (double) scale;
                double screenEnd   = fits
                    ? (renderX + tabWidth + 4 + xOffset) * (double) scale
                    : (tabAreaEnd + 4 + xOffset) * (double) scale;

                if (!fits && (!hasRight || i != panel.tabLastRendered + 1)) break;

                if (mouseX >= screenStart && mouseX < screenEnd) {
                    if (button == 0) {
                        panel.activeTab = i;
                        tab.unreadCount = 0;
                    } else if (button == 1) {
                        ContextMenu.open(panel, tab, (int) mouseX, (int) mouseY);
                    }
                    cir.setReturnValue(true);
                    return;
                }

                if (!fits) break;
                renderX += tabWidth + 1;
            }
        }
    }

    // ------------------------------------------------------------------
    // Send message — apply context prefix
    // ------------------------------------------------------------------

    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void interceptSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        String text = normalize(chatText);
        if (text.isEmpty() || text.startsWith("/")) return;

        ChatTab ctxTab = ChatRenderer.contextTab;
        if (ctxTab == null || ctxTab.contextPrefix.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        String full = ctxTab.contextPrefix + " " + text;
        ci.cancel();
        if (addToHistory) client.inGameHud.getChatHud().addToMessageHistory(full);
        if (ctxTab.contextCommandMode) {
            client.getNetworkHandler().sendChatCommand(full);
        } else {
            client.getNetworkHandler().sendChatMessage(full);
        }
    }
}
