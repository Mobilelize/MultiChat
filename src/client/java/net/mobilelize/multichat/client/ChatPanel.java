package net.mobilelize.multichat.client;

import net.minecraft.client.gui.hud.ChatHudLine;

import java.util.ArrayList;
import java.util.List;

public class ChatPanel {

    public enum Anchor { LEFT, RIGHT }

    public final Anchor anchor;
    public final int margin;
    public final List<ChatHudLine.Visible> messages = new ArrayList<>();
    public int scrolledLines = 0;

    public ChatPanel(Anchor anchor, int margin) {
        this.anchor = anchor;
        this.margin = margin;
    }

    public int computeXOffset(int scaledWindowWidth, float scale, int chatWidth) {
        if (anchor == Anchor.RIGHT) {
            return Math.round(scaledWindowWidth / scale) - chatWidth - 12 - margin;
        }
        return margin;
    }
}
