package dev.lilbig.aimbot.math;

import java.util.Random;

public final class RotationMath {
    private static final Random RNG = new Random();
    public static final float MAX_YAW_TICK   = 19.5f;
    public static final float MAX_PITCH_TICK = 13.5f;
    private RotationMath() {}

    public static float angleDiff(float a, float b) {
        float d = a - b;
        while (d < -180f) d += 360f;
        while (d >  180f) d -= 360f;
        return d;
    }

    public static float smoothStep(float current, float target, float speed) {
        float delta      = angleDiff(target, current);
        float noise      = (RNG.nextFloat() - 0.5f) * 0.006f;
        float vSpeed     = speed + noise;
        float distFactor = Math.min(Math.abs(delta) / 35.0f, 1.0f);
        float step       = delta * vSpeed * (0.55f + 0.45f * distFactor);
        float maxStep    = MAX_YAW_TICK + RNG.nextFloat() * 2.5f;
        if (Math.abs(step) > maxStep) step = Math.signum(step) * maxStep;
        return current + step;
    }

    public static float[] clampDelta(float newYaw, float newPitch,
                                      float lastYaw, float lastPitch) {
        float dy       = angleDiff(newYaw,   lastYaw);
        float dp       = angleDiff(newPitch, lastPitch);
        float capYaw   = MAX_YAW_TICK   + RNG.nextFloat() * 1.5f;
        float capPitch = MAX_PITCH_TICK + RNG.nextFloat() * 1.0f;
        if (Math.abs(dy) > capYaw)   dy = Math.signum(dy)   * capYaw;
        if (Math.abs(dp) > capPitch) dp = Math.signum(dp) * capPitch;
        return new float[]{ lastYaw + dy, lastPitch + dp };
    }

    public static float[] injectJitter(float yaw, float pitch) {
        float jY = (float)(RNG.nextGaussian() * 0.003);
        float jP = (float)(RNG.nextGaussian() * 0.002);
        if (RNG.nextFloat() < 0.04f) jY += (RNG.nextFloat() - 0.5f) * 0.018f;
        if (RNG.nextFloat() < 0.03f) jP += (RNG.nextFloat() - 0.5f) * 0.012f;
        return new float[]{ yaw + jY, pitch + jP };
    }
}
