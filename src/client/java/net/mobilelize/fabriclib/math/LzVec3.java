package net.mobilelize.fabriclib.math;

import net.minecraft.util.math.Vec3d;

/**
 * Stable wrapper for a 3D floating-point vector.
 * 1.21 (Yarn): net.minecraft.util.math.Vec3d
 * 26.1 (Mojang): net.minecraft.world.phys.Vec3
 */
public final class LzVec3 {

    private final Vec3d vec;

    public LzVec3(Vec3d vec) { this.vec = vec; }

    // ── Factories ────────────────────────────────────────────────────────────

    // 1.21: new Vec3d(x, y, z)   26.1: new Vec3(x, y, z)
    public static LzVec3 of(double x, double y, double z) { return new LzVec3(new Vec3d(x, y, z)); }
    public static LzVec3 of(Vec3d vec)                    { return new LzVec3(vec); }
    // 1.21: Vec3d.ZERO   26.1: Vec3.ZERO
    public static LzVec3 zero()                            { return new LzVec3(Vec3d.ZERO); }

    // ── Components ───────────────────────────────────────────────────────────

    // 1.21: vec.x / vec.y / vec.z fields   26.1: stable
    public double x() { return vec.x; }
    public double y() { return vec.y; }
    public double z() { return vec.z; }

    // ── Math ─────────────────────────────────────────────────────────────────

    public LzVec3 add(double dx, double dy, double dz) { return new LzVec3(vec.add(dx, dy, dz)); }
    public LzVec3 add(LzVec3 other)                    { return new LzVec3(vec.add(other.vec)); }
    public LzVec3 subtract(LzVec3 other)               { return new LzVec3(vec.subtract(other.vec)); }

    /** 1.21: vec.multiply(f,f,f)   26.1: vec.scale(f) */
    public LzVec3 scale(double factor)   { return new LzVec3(vec.multiply(factor, factor, factor)); }
    public LzVec3 normalize()            { return new LzVec3(vec.normalize()); }
    public double length()               { return vec.length(); }

    /** 1.21: vec.lengthSquared()   26.1: vec.lengthSqr() */
    public double squaredLength()        { return vec.lengthSquared(); }
    public double distanceTo(LzVec3 other) { return vec.distanceTo(other.vec); }

    public double squaredDistanceTo(LzVec3 other) {
        double dx = x() - other.x(), dy = y() - other.y(), dz = z() - other.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** 1.21: vec.dotProduct(other)   26.1: vec.dot(other) */
    public double dot(LzVec3 other)   { return vec.dotProduct(other.vec); }

    /** 1.21: vec.crossProduct(other)   26.1: vec.cross(other) */
    public LzVec3 cross(LzVec3 other) { return new LzVec3(vec.crossProduct(other.vec)); }

    public LzVec3 lerp(LzVec3 other, double t) {
        return LzVec3.of(x() + (other.x() - x()) * t, y() + (other.y() - y()) * t, z() + (other.z() - z()) * t);
    }

    /** Raw MC Vec3d — escape hatch. */
    public Vec3d mc() { return vec; }

    @Override public String toString()        { return "(" + x() + ", " + y() + ", " + z() + ")"; }
    @Override public boolean equals(Object o) { return o instanceof LzVec3 other && vec.equals(other.vec); }
    @Override public int hashCode()           { return vec.hashCode(); }
}
