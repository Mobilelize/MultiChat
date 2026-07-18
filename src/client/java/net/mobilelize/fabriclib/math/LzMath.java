package net.mobilelize.fabriclib.math;

import net.minecraft.util.math.MathHelper;

/**
 * Common math utilities.
 * 1.21 (Yarn): net.minecraft.util.math.MathHelper
 * 26.1 (Mojang): net.minecraft.util.Mth
 */
public final class LzMath {

    private LzMath() {}

    // 1.21: MathHelper.ceil(f)   26.1: Mth.ceil(f)
    public static int ceil(float f)  { return MathHelper.ceil(f); }
    public static int ceil(double d) { return MathHelper.ceil(d); }

    // 1.21: MathHelper.floor(d)   26.1: Mth.floor(d)
    public static int floor(float f)  { return MathHelper.floor(f); }
    public static int floor(double d) { return MathHelper.floor(d); }

    // 1.21: MathHelper.clamp(v, min, max)   26.1: Mth.clamp(v, min, max)
    public static int    clamp(int v,    int min,    int max)    { return MathHelper.clamp(v, min, max); }
    public static float  clamp(float v,  float min,  float max)  { return MathHelper.clamp(v, min, max); }
    public static double clamp(double v, double min, double max) { return MathHelper.clamp(v, min, max); }

    // 1.21: MathHelper.lerp(t, a, b)   26.1: Mth.lerp(t, a, b)
    public static double lerp(double t, double a, double b) { return MathHelper.lerp(t, a, b); }
    public static float  lerp(float t,  float a,  float b)  { return MathHelper.lerp(t, a, b); }
}
