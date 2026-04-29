package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {

    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> getVisibleMessages();

    @Accessor("scrolledLines")
    int getScrolledLines();

    @Accessor("hasUnreadNewMessages")
    boolean getHasUnreadNewMessages();

    @Accessor("client")
    MinecraftClient getClient();
}
