package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggestorAccessor {
    @Invoker("isOpen")
    boolean invokeIsOpen();

    @Accessor("window")
    ChatInputSuggestor.SuggestionWindow getWindow();
}
