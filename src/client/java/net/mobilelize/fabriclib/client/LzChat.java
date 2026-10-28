package net.mobilelize.fabriclib.client;

import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.Gui;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Utilities for the vanilla ChatComponent.
 * 1.21 (Yarn): net.minecraft.client.gui.hud.ChatHud / net.minecraft.client.util.ChatMessages
 * 26.1 (Mojang): net.minecraft.client.gui.components.ChatComponent / mc().font.split(...)
 */
public final class LzChat {

    private LzChat() {}

    // 1.21: client.inGameHud   26.1: client.gui
    @Nullable
    private static Gui gui() { return LzClient.mc().gui; }

    // 1.21: inGameHud.getChatHud()   26.1: client.gui.getChat()
    @Nullable
    public static ChatComponent chatHud() {
        var g = gui();
        return g == null ? null : g.getChat();
    }

    // 1.21.11: getChatScale() — read option directly
    // 1.21: options.getChatScale().getValue()   26.1: options.chatScale().get()
    public static double chatScale() {
        return LzOptions.chatScale();
    }

    // 1.21.11: getWidth() (instance) went private — public static ChatHud.getWidth(double) still exists
    // 26.1: ChatComponent.getWidth(double) static
    public static int chatWidth() {
        return ChatComponent.getWidth(LzOptions.chatWidth());
    }

    // 1.21: inGameHud.getChatHud().getVisibleLineCount()   26.1: VERIFY
    public static int visibleLineCount() {
        var hud = chatHud();
        return hud == null ? 0 : hud.getLinesPerPage();
    }

    // 1.21: inGameHud.getTicks()   26.1: client.gui.getGuiTicks()
    public static int ticks() {
        var g = gui();
        return g == null ? 0 : g.getGuiTicks();
    }

    // 1.21: inGameHud.getChatHud().addToMessageHistory(msg)   26.1: VERIFY
    public static void addToMessageHistory(String msg) {
        var hud = chatHud();
        if (hud != null) hud.addRecentChat(msg);
    }

    // 1.21: chatLineSpacing drives line height   26.1: VERIFY
    public static int chatLineHeight() {
        return (int)(9.0 * (LzOptions.chatLineSpacing() + 1.0));
    }

    private static final FormattedCharSequence INDENT = FormattedCharSequence.codepoint(32, Style.EMPTY);

    // net.minecraft.client.gui.ComponentRenderUtils#wrapComponents
    private static String stripColor(String text) {
        return LzOptions.chatColors() ? text : ChatFormatting.stripFormatting(text);
    }

    /**
     * Breaks a Component into rendered lines that fit within {@code width} chat units.
     * 1.21: ChatMessages.breakRenderedChatMessageLines(text, width, textRenderer)
     * 26.1: ComponentRenderUtils.wrapComponents(text, width, font)  — returns List<FormattedCharSequence>, indents wrapped lines
     */
    public static List<LzOrderedText> breakLines(LzText text, int width) {
        FormattedText message = text.mc();
        var font = LzClient.mc().font;

        ComponentCollector collector = new ComponentCollector();
        message.visit((style, contents) -> {
            collector.append(FormattedText.of(stripColor(contents), style));
            return Optional.empty();
        }, Style.EMPTY);

        final List<FormattedCharSequence> lines = Lists.newArrayList();
        font.getSplitter().splitLines(collector.getResultOrEmpty(), width, Style.EMPTY, (lineText, wrapped) -> {
            FormattedCharSequence reordered = Language.getInstance().getVisualOrder(lineText);
            lines.add(wrapped ? FormattedCharSequence.composite(INDENT, reordered) : reordered);
        });
        if (lines.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
        return lines.stream().map(LzOrderedText::wrap).toList();
    }
}
