package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandSuggestions.class)
public interface ChatInputSuggestorAccessor {
    @Invoker("isVisible")
    boolean invokeIsOpen();

    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList getWindow();
}
