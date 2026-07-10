package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("defaultHandleClickEvent")
    static void invokeHandleClickEvent(ClickEvent event, Minecraft client, Screen screen) {
        throw new AssertionError();
    }
}
