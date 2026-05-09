package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.MathHelper;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.render.ChatRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        var chat  = client.inGameHud.getChatHud();
        float scale    = (float) chat.getChatScale();
        int chatWidth  = MathHelper.ceil((float) chat.getWidth() / scale);
        int windowHeight = client.getWindow().getScaledHeight();
        int windowWidth  = client.getWindow().getScaledWidth();

        int topY = MathHelper.floor((float)(windowHeight - 40) / scale);

        // Tab bar occupies [topY, topY + TAB_HEIGHT] in scaled space
        // → screen y range: [topY * scale, (topY + TAB_HEIGHT) * scale]
        double tabBarScreenTop = topY * (double) scale;
        double tabBarScreenBot = (topY + ChatRenderer.TAB_HEIGHT) * (double) scale;

        if (mouseY < tabBarScreenTop || mouseY > tabBarScreenBot) return;

        for (ChatPanel panel : ChatRenderer.PANELS) {
            if (!panel.enabled) continue;

            int xOffset = panel.computeXOffset(windowWidth, scale, chatWidth);

            // Walk tab buttons left-to-right to find which was clicked
            // Render starts at x = -4 in scaled+translated space.
            // screen_x = (render_x + 4 + xOffset) * scale
            int renderX = -4;

            for (int i = 0; i < panel.tabs.size(); i++) {
                ChatTab tab = panel.tabs.get(i);
                String label = ChatRenderer.getTabLabel(tab);
                int tabWidth = client.textRenderer.getWidth(label) + 8;

                double screenXStart = (renderX + 4 + xOffset) * (double) scale;
                double screenXEnd   = (renderX + tabWidth + 4 + xOffset) * (double) scale;

                if (mouseX >= screenXStart && mouseX < screenXEnd) {
                    panel.activeTab = i;
                    tab.unreadCount = 0;
                    cir.setReturnValue(true);
                    return;
                }

                renderX += tabWidth + 1;
            }
        }
    }
}
