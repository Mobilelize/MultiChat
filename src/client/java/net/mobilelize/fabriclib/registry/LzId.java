package net.mobilelize.fabriclib.registry;

import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Stable wrapper for a resource location / identifier.
 * 1.21 (Yarn): net.minecraft.util.Identifier
 * 26.1 (Mojang): net.minecraft.resources.ResourceLocation
 */
public final class LzId {

    private final Identifier id;

    private LzId(Identifier id) { this.id = id; }

    // ── Factories ────────────────────────────────────────────────────────────

    // 1.21: Identifier.of(ns, path)   26.1: ResourceLocation.fromNamespaceAndPath(ns, path)
    public static LzId of(String namespace, String path) {
        return new LzId(Identifier.of(namespace, path));
    }

    // 1.21: Identifier.tryParse(combined)   26.1: ResourceLocation.tryParse(combined)
    public static Optional<LzId> tryParse(String combined) {
        return Optional.ofNullable(Identifier.tryParse(combined)).map(LzId::new);
    }

    /** Wraps an existing MC Identifier — use only at bridge boundaries. */
    public static LzId wrapMc(Identifier id) { return new LzId(id); }

    // ── Accessors ────────────────────────────────────────────────────────────

    // unchanged: id.getPath() / id.getNamespace()
    public String path()      { return id.getPath(); }
    public String namespace() { return id.getNamespace(); }

    @Override public String toString()        { return id.toString(); }
    @Override public boolean equals(Object o) { return o instanceof LzId other && id.equals(other.id); }
    @Override public int hashCode()           { return id.hashCode(); }

    // ── Package-private bridge access ─────────────────────────────────────────

    public Identifier mc() { return id; }
}
