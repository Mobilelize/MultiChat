package net.mobilelize.fabriclib.registry;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Stable wrapper for a resource location / identifier.
 * 1.21 (Yarn): net.minecraft.util.Identifier
 * 26.1 (Mojang): net.minecraft.resources.Identifier
 */
public final class LzId {

    private final Identifier id;

    private LzId(Identifier id) { this.id = id; }

    // ── Factories ────────────────────────────────────────────────────────────

    // 1.21: Identifier.of(ns, path)   26.1: Identifier.fromNamespaceAndPath(ns, path)
    public static LzId of(String namespace, String path) {
        return new LzId(Identifier.fromNamespaceAndPath(namespace, path));
    }

    // 1.21: Identifier.tryParse(combined)   26.1: Identifier.tryParse(combined)
    public static Optional<LzId> tryParse(String combined) {
        return Optional.ofNullable(Identifier.tryParse(combined)).map(LzId::new);
    }

    /** Wraps an existing MC Identifier — use only at bridge boundaries. */
    public static LzId wrapMc(Identifier id) { return new LzId(id); }

    // ── Accessors ────────────────────────────────────────────────────────────

    // 1.21: id.getPath() / id.getNamespace()   26.1: stable
    public String path()      { return id.getPath(); }
    public String namespace() { return id.getNamespace(); }

    @Override public String toString()        { return id.toString(); }
    @Override public boolean equals(Object o) { return o instanceof LzId other && id.equals(other.id); }
    @Override public int hashCode()           { return id.hashCode(); }

    // ── Package-private bridge access ─────────────────────────────────────────

    public Identifier mc() { return id; }
}
