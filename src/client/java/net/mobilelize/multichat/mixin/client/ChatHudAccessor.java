package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("hasUnreadNewMessages")
    boolean getHasUnreadNewMessages();
}
