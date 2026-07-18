package net.mobilelize.fabriclib.client;

import net.minecraft.world.entity.player.ChatVisiblity;

/**
 * Visibility setting for the chat HUD.
 * 1.21 (Yarn): net.minecraft.network.message.ChatVisibility
 * 26.1 (Mojang): net.minecraft.world.entity.player.ChatVisiblity  (Mojang typo — single 'i')
 */
public enum LzChatVisibility {

    FULL  (ChatVisiblity.FULL),
    SYSTEM(ChatVisiblity.SYSTEM),
    HIDDEN(ChatVisiblity.HIDDEN);

    private final ChatVisiblity mc;

    LzChatVisibility(ChatVisiblity mc) { this.mc = mc; }

    /** Raw MC ChatVisiblity — escape hatch. */
    public ChatVisiblity mc() { return mc; }

    public static LzChatVisibility fromMC(ChatVisiblity v) {
        return switch (v) {
            case FULL   -> FULL;
            case SYSTEM -> SYSTEM;
            case HIDDEN -> HIDDEN;
        };
    }
}
