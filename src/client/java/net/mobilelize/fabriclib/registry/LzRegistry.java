package net.mobilelize.fabriclib.registry;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry lookups.
 * 1.21 (Yarn): net.minecraft.registry.Registries / Registry
 * 26.1 (Mojang): net.minecraft.core.registries.BuiltInRegistries / Registry  (VERIFY)
 */
public final class LzRegistry {

    private LzRegistry() {}

    // 1.21: registry.getIds()   26.1: registry.keySet()  (VERIFY)
    public static Set<String> ids(Registry<?> registry) {
        return registry.getIds().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    // 1.21: registry.get(id)   26.1: registry.get(id)  (stable)
    public static <T> Optional<T> get(Registry<T> registry, String id) {
        return LzId.tryParse(id).map(lzId -> registry.get(lzId.mc()));
    }

    // 1.21: registry.getId(value)   26.1: registry.getKey(value).location()  (VERIFY)
    public static <T> Optional<String> getId(Registry<T> registry, T value) {
        return Optional.ofNullable(registry.getId(value)).map(Object::toString);
    }

    // 1.21: Registries.ENTITY_TYPE   26.1: BuiltInRegistries.ENTITY_TYPE
    public static Set<String> entityTypeIds() { return ids(Registries.ENTITY_TYPE); }

    // 1.21: Registries.ITEM   26.1: BuiltInRegistries.ITEM
    public static Set<String> itemIds()       { return ids(Registries.ITEM); }

    // 1.21: Registries.BLOCK   26.1: BuiltInRegistries.BLOCK
    public static Set<String> blockIds()      { return ids(Registries.BLOCK); }

    // 1.21: Registries.SOUND_EVENT   26.1: BuiltInRegistries.SOUND_EVENT  (VERIFY)
    public static Set<String> soundIds() {
        return Registries.SOUND_EVENT.getIds().stream()
            .map(Object::toString)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
