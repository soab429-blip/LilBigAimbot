package dev.lilbig.aimbot.math;

public final class GCDFix {
    private static float gcdX = 0.0f;
    private static float gcdY = 0.0f;
    private GCDFix() {}

    public static void seed(float vanillaSens) {
        float f  = vanillaSens * 0.6f + 0.2f;
        float gcd = f * f * f * 8.0f;
        gcdX = gcd;
        gcdY = gcd;
    }

    public static float[] apply(float newYaw, float newPitch,
                                 float lastYaw, float lastPitch) {
        if (gcdX == 0.0f) return new float[]{newYaw, newPitch};
        float dYaw   = newYaw   - lastYaw;
        float dPitch = newPitch - lastPitch;
        float snappedDYaw   = Math.round(dYaw   / gcdX) * gcdX;
        float snappedDPitch = Math.round(dPitch / gcdY) * gcdY;
        return new float[]{ lastYaw + snappedDYaw, lastPitch + snappedDPitch };
    }
}
