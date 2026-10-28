package net.mobilelize.fabriclib.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Utilities for the vanilla ChatHud.
 * 1.21 (Yarn): net.minecraft.client.gui.hud.ChatHud / net.minecraft.client.util.ChatMessages
 * 26.1 (Mojang): VERIFY class/method renames
 */
public final class LzChat {

    private LzChat() {}

    // 1.21: client.inGameHud   26.1: client.gui  (VERIFY)
    @Nullable
    private static Gui inGameHud() { return LzClient.mc().gui; }

    // 1.21: inGameHud.getChatHud()   26.1: VERIFY
    @Nullable
    public static ChatComponent chatHud() {
        var hud = inGameHud();
        return hud == null ? null : hud.getChat();
    }

    // 1.21.11: getChatScale() went private — read option directly
    public static double chatScale() {
        return (double) LzClient.mc().options.chatScale().get();
    }

    // 1.21.11: getWidth() (instance) went private — public static ChatHud.getWidth(double) still exists
    public static int chatWidth() {
        return ChatComponent.getWidth((double) LzClient.mc().options.chatWidth().get());
    }

    // 1.21: inGameHud.getChatHud().getVisibleLineCount()   26.1: VERIFY
    public static int visibleLineCount() {
        var hud = inGameHud();
        return hud == null ? 0 : hud.getChat().getLinesPerPage();
    }

    // 1.21: inGameHud.getTicks()   26.1: VERIFY
    public static int ticks() {
        var hud = inGameHud();
        return hud == null ? 0 : hud.getGuiTicks();
    }

    // 1.21: inGameHud.getChatHud().addToMessageHistory(msg)   26.1: VERIFY
    public static void addToMessageHistory(String msg) {
        var hud = inGameHud();
        if (hud != null) hud.getChat().addRecentChat(msg);
    }

    // 1.21: chatLineSpacing drives line height   26.1: VERIFY
    public static int chatLineHeight() {
        return (int)(9.0 * (LzOptions.chatLineSpacing() + 1.0));
    }

    /**
     * Breaks a Text into rendered lines that fit within {@code width} chat units.
     * 1.21: ChatMessages.breakRenderedChatMessageLines(text, width, textRenderer)
     * 26.1: VERIFY method/class rename
     */
    public static List<LzOrderedText> breakLines(LzText text, int width) {
        return ComponentRenderUtils.wrapComponents(
            text.mc(), width, LzClient.mc().font)
            .stream().map(LzOrderedText::wrap).toList();
    }
}
