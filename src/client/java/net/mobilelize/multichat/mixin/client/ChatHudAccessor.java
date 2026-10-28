package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
    @Accessor("newMessageSinceScroll")
    boolean getHasUnreadNewMessages();
}
