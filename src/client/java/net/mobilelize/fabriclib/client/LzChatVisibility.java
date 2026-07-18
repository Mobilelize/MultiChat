package net.mobilelize.fabriclib.client;

import net.minecraft.network.message.ChatVisibility;

/**
 * Visibility setting for the chat HUD.
 * 1.21 (Yarn): net.minecraft.network.message.ChatVisibility
 * 26.1 (Mojang): VERIFY package/class rename
 */
public enum LzChatVisibility {

    FULL  (ChatVisibility.FULL),
    SYSTEM(ChatVisibility.SYSTEM),
    HIDDEN(ChatVisibility.HIDDEN);

    private final ChatVisibility mc;

    LzChatVisibility(ChatVisibility mc) { this.mc = mc; }

    /** Raw MC ChatVisibility — escape hatch. */
    public ChatVisibility mc() { return mc; }

    public static LzChatVisibility fromMC(ChatVisibility v) {
        return switch (v) {
            case FULL   -> FULL;
            case SYSTEM -> SYSTEM;
            case HIDDEN -> HIDDEN;
        };
    }
}
