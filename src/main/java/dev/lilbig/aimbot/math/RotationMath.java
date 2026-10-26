package dev.lilbig.aimbot.math;

import java.util.Random;

public final class RotationMath {

    private static final Random RNG = new Random();

    public static final float MAX_YAW_TICK   = 19.5f;
    public static final float MAX_PITCH_TICK = 13.5f;

    // ── OU tremor state (phase 4) ────────────────────────────────────────────
    private static float ouVelY  = 0f, ouVelP  = 0f;
    private static final float OU_THETA = 0.18f;
    private static final float OU_SIGMA = 0.011f;

    // ── multi-correction state (phase 5) ─────────────────────────────────────
    private static float overshootY = 0f, overshootP = 0f;
    private static float corrAmpY   = 0f, corrAmpP   = 0f;
    private static int   corrLeft   = 0;
    private static final float AMP_DECAY = 0.42f;

    // ── ballistic phase state (phase 2) ──────────────────────────────────────
    private static boolean inBallistic  = false;
    private static float   ballisticTTL = 0f;   // ticks remaining in arm-throw
    private static float   ballisticMul = 1f;   // speed multiplier during throw

    private RotationMath() {}

    // ─────────────────────────────────────────────────────────────────────────
    // angleDiff — shortest signed arc between two yaw/pitch values
    // ─────────────────────────────────────────────────────────────────────────
    public static float angleDiff(float a, float b) {
        float d = a - b;
        while (d < -180f) d += 360f;
        while (d >  180f) d -= 360f;
        return d;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // seedOvershoot — called on new target lock
    // arms ballistic sweep + plants first overshoot amplitude
    // ─────────────────────────────────────────────────────────────────────────
    public static void seedOvershoot() {
        // phase 2 seed: 3-5 tick arm throw, speed 1.6-2.2x normal
        inBallistic  = true;
        ballisticTTL = 3f + RNG.nextFloat() * 2f;
        ballisticMul = 1.6f + RNG.nextFloat() * 0.6f;

        // phase 5 seed: first overshoot is 8-18% of remaining delta
        // AimbotModule will call burnOvershoot* after bezier lands
        overshootY = (RNG.nextFloat() * 0.10f + 0.08f) * (RNG.nextBoolean() ? 1f : -1f);
        overshootP = overshootY * (0.4f + RNG.nextFloat() * 0.3f);
        corrAmpY   = overshootY;
        corrAmpP   = overshootP;
        corrLeft   = 2 + RNG.nextInt(2); // 2 or 3 damped loops
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resetOU — called when target lost
    // ─────────────────────────────────────────────────────────────────────────
    public static void resetOU() {
        ouVelY = 0f; ouVelP = 0f;
        overshootY = 0f; overshootP = 0f;
        corrAmpY = 0f; corrAmpP = 0f;
        corrLeft = 0;
        inBallistic = false;
        ballisticTTL = 0f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ballisticStep — phase 2: fast coarse arm throw toward target
    // high speed, intentionally overshoots, no fine precision
    // transitions to bezier once TTL expires
    // returns: [newYaw, newPitch, stillBallistic]
    // ─────────────────────────────────────────────────────────────────────────
    public static float[] ballisticStep(float curYaw, float curPitch,
                                         float tgtYaw, float tgtPitch) {
        if (!inBallistic) return new float[]{curYaw, curPitch, 0f};

        ballisticTTL -= 1f;
        if (ballisticTTL <= 0f) {
            inBallistic = false;
            return new float[]{curYaw, curPitch, 0f};
        }

        // arm throw: linear-ish, fast, slightly noisy — no bezier easing here
        float dY = angleDiff(tgtYaw,   curYaw);
        float dP = angleDiff(tgtPitch, curPitch);

        // coarse speed: ballisticMul * base, no distance falloff (arm doesn't care)
        float stepY = dY * 0.38f * ballisticMul + (RNG.nextFloat() - 0.5f) * 0.8f;
        float stepP = dP * 0.38f * ballisticMul + (RNG.nextFloat() - 0.5f) * 0.5f;

        // clamp to max but allow higher than fine-motor phase
        float capY = MAX_YAW_TICK * 1.35f;
        float capP = MAX_PITCH_TICK * 1.35f;
        if (Math.abs(stepY) > capY) stepY = Math.signum(stepY) * capY;
        if (Math.abs(stepP) > capP) stepP = Math.signum(stepP) * capP;

        // speed drops tick-by-tick as arm decelerates into wrist handoff
        ballisticMul *= (0.78f + RNG.nextFloat() * 0.08f);

        return new float[]{curYaw + stepY, curPitch + stepP, 1f};
    }

    public static boolean isBallistic() { return inBallistic; }

    // ─────────────────────────────────────────────────────────────────────────
    // bezierStep — phase 3: wrist fine-motor deceleration curve
    // speed falls off hard as delta shrinks — the arm-to-wrist handoff
    // ─────────────────────────────────────────────────────────────────────────
    public static float bezierStep(float current, float target, float speed) {
        float delta      = angleDiff(target, current);
        float noise      = (RNG.nextFloat() - 0.5f) * 0.006f;
        float vSpeed     = speed + noise;

        // distFactor: near-zero when close (wrist barely moves), ramps up with distance
        float distFactor = Math.min(Math.abs(delta) / 35.0f, 1.0f);
        float step       = delta * vSpeed * (0.55f + 0.45f * distFactor);

        float maxStep = MAX_YAW_TICK + RNG.nextFloat() * 2.5f;
        if (Math.abs(step) > maxStep) step = Math.signum(step) * maxStep;

        return current + step;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // burnOvershootYaw / burnOvershootPitch — phase 5 tick 1
    // applies the first overshoot delta after bezier lands near target
    // subsequent corrections called via correctStep each tick
    // ─────────────────────────────────────────────────────────────────────────
    public static float burnOvershootYaw(float yaw) {
        float delta = corrAmpY;
        // reduce for next loop
        corrAmpY *= -AMP_DECAY;
        return yaw + delta;
    }

    public static float burnOvershootPitch(float pitch) {
        float delta = corrAmpP;
        corrAmpP *= -AMP_DECAY;
        return pitch + delta;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // correctStep — phase 5 ticks 2-3: damped micro-corrections
    // each call flips sign and shrinks amplitude — pendulum dying out
    // returns [dYaw, dPitch] to add, or [0,0] when corrections exhausted
    // ─────────────────────────────────────────────────────────────────────────
    public static float[] correctStep() {
        if (corrLeft <= 0) return new float[]{0f, 0f};
        corrLeft--;
        float dY = corrAmpY;
        float dP = corrAmpP;
        corrAmpY *= -AMP_DECAY;
        corrAmpP *= -AMP_DECAY;
        return new float[]{dY, dP};
    }

    public static boolean hasCorrections() { return corrLeft > 0; }

    // ─────────────────────────────────────────────────────────────────────────
    // ouStep — phase 4: Ornstein-Uhlenbeck muscle tremor
    // mean-reverting so it never drifts — just oscillates near zero
    // ─────────────────────────────────────────────────────────────────────────
    public static float[] ouStep() {
        ouVelY += -OU_THETA * ouVelY + OU_SIGMA * (float) RNG.nextGaussian();
        ouVelP += -OU_THETA * ouVelP + OU_SIGMA * 0.65f * (float) RNG.nextGaussian();
        return new float[]{ouVelY, ouVelP};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // injectJitter — micro high-freq noise on every tick, rare spikes
    // ─────────────────────────────────────────────────────────────────────────
    public static float[] injectJitter(float yaw, float pitch) {
        float jY = (float)(RNG.nextGaussian() * 0.003);
        float jP = (float)(RNG.nextGaussian() * 0.002);
        if (RNG.nextFloat() < 0.04f) jY += (RNG.nextFloat() - 0.5f) * 0.018f;
        if (RNG.nextFloat() < 0.03f) jP += (RNG.nextFloat() - 0.5f) * 0.012f;
        return new float[]{yaw + jY, pitch + jP};
    }

    // ─────────────────────────────────────────────────────────────────────────
    // clampDelta — hard cap per-tick rotation, randomised slightly
    // ─────────────────────────────────────────────────────────────────────────
    public static float[] clampDelta(float newYaw, float newPitch,
                                      float lastYaw, float lastPitch) {
        float dy  = angleDiff(newYaw,   lastYaw);
        float dp  = angleDiff(newPitch, lastPitch);
        float capY = MAX_YAW_TICK   + RNG.nextFloat() * 1.5f;
        float capP = MAX_PITCH_TICK + RNG.nextFloat() * 1.0f;
        if (Math.abs(dy) > capY) dy = Math.signum(dy) * capY;
        if (Math.abs(dp) > capP) dp = Math.signum(dp) * capP;
        return new float[]{lastYaw + dy, lastPitch + dp};
    }
}
