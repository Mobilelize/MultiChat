package net.mobilelize.fabriclib.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry lookups.
 * 1.21 (Yarn): net.minecraft.registry.Registries / Registry
 * 26.1 (Mojang): net.minecraft.core.registries.BuiltInRegistries / net.minecraft.core.Registry
 */
public final class LzRegistry {

    private LzRegistry() {}

    // 1.21: registry.getIds()   26.1: registry.keySet() then .location()
    public static <T> Set<String> ids(Registry<T> registry) {
        return registry.keySet().stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }

    // 1.21: registry.get(id)   26.1: registry.get(ResourceLocation) via Optional
    public static <T> Optional<T> get(Registry<T> registry, String id) {
        return LzId.tryParse(id).flatMap(lzId -> registry.getOptional(lzId.mc()));
    }

    // 1.21: registry.getId(value)   26.1: registry.getKey(value).map(ResourceKey::location)
    public static <T> Optional<String> getId(Registry<T> registry, T value) {
        return registry.getResourceKey(value).map(k -> k.registryKey().toString());
    }

    // 1.21: Registries.ENTITY_TYPE   26.1: BuiltInRegistries.ENTITY_TYPE
    public static Set<String> entityTypeIds() { return ids(BuiltInRegistries.ENTITY_TYPE); }

    // 1.21: Registries.ITEM   26.1: BuiltInRegistries.ITEM
    public static Set<String> itemIds()       { return ids(BuiltInRegistries.ITEM); }

    // 1.21: Registries.BLOCK   26.1: BuiltInRegistries.BLOCK
    public static Set<String> blockIds()      { return ids(BuiltInRegistries.BLOCK); }

    public static Set<String> soundIds() {
        return BuiltInRegistries.SOUND_EVENT.keySet().stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
