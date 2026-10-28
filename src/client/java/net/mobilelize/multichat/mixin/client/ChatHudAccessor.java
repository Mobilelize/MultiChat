package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatHudAccessor {
    @Accessor("newMessageSinceScroll")
    boolean getHasUnreadNewMessages();

    // 26.1: the public add methods each force a source/tag (PLAYER can be hidden by server chat
    // restrictions, addClientSystemMessage adds a system tag). This is 1.21's addMessage(msg, null, null).
    @Invoker("addMessage")
    void invokeAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag);
}
