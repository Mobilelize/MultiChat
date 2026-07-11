package net.mobilelize.multichat.mixin.client;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public interface ChatInputSuggestorWindowAccessor {
    @Accessor("area")
    Rect2i getArea();
}
