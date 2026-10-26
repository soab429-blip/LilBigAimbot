package dev.lilbig.aimbot.module;

import dev.lilbig.aimbot.AimbotMod;
import dev.lilbig.aimbot.math.GCDFix;
import dev.lilbig.aimbot.math.HitValidation;
import dev.lilbig.aimbot.math.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;
import java.util.stream.*;

public class AimbotModule {

    public  boolean          enabled       = false;
    public  float            fovFilter     = 180f, maxReach = 3.5f;

    public float             smoothSpeed, deadzoneAngle, missChance, eyeOffsetMul;
    public long              reactionDelayMax  = 250L;
    public float             ballisticPower    = 1.9f;
    public int               ballisticTicks    = 4;
    public float             corrDecay         = 0.42f;
    public int               corrLoops         = 2;
    private long             attackDelayBase, attackDelayVar, lastAttack = 0L;

    private final Random     RNG           = new Random();
    private final Minecraft  mc            = Minecraft.getMinecraft();
    public  EntityLivingBase currentTarget = null;
    private       EntityLivingBase lastTarget    = null;

    // ── reaction delay state (phase 1) ───────────────────────────────────────
    private long   targetAcquiredAt  = 0L;
    private long   reactionDelayMs   = 0L;

    // ── micro-pause state ─────────────────────────────────────────────────────
    private int    microPauseTicks   = 0;

    // ── correction loop state (phase 5) ──────────────────────────────────────
    // NEAR_ZONE: crosshair close enough that bezier hands off to correction phase
    private boolean inCorrectionPhase = false;
    private int     correctionHoldTicks = 0;   // ticks to hold before next correction tick

    public AimbotModule() { rollEngagementParams(); }

    // ─────────────────────────────────────────────────────────────────────────
    // rollEngagementParams — randomise per-target personality
    // ─────────────────────────────────────────────────────────────────────────
    private void rollEngagementParams() {
        smoothSpeed      = 0.22f + RNG.nextFloat() * 0.18f;
        deadzoneAngle    = 0.45f + RNG.nextFloat() * 0.55f;
        attackDelayBase  = 480L  + (long)(RNG.nextFloat() * 120L);
        attackDelayVar   = 80L   + (long)(RNG.nextFloat() * 60L);
        missChance       = 0.06f + RNG.nextFloat() * 0.08f;
        eyeOffsetMul     = 0.65f + RNG.nextFloat() * 0.35f;
        // reaction delay: log-normal 150-250ms (phase 1)
        reactionDelayMs  = 150L + (long)(RNG.nextFloat() * (reactionDelayMax - 150L));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        GCDFix.seed(mc.gameSettings.mouseSensitivity);
        if (e.phase != TickEvent.Phase.END) return;
        if (!enabled) { currentTarget = null; return; }

        EntityLivingBase target = findTarget();
        currentTarget = target;

        if (target == null) {
            lastTarget = null;
            RotationMath.resetOU();
            inCorrectionPhase = false;
            return;
        }

        // ── new target acquired — arm reaction timer, seed ballistic + overshoot
        if (target != lastTarget) {
            lastTarget         = target;
            targetAcquiredAt   = System.currentTimeMillis();
            rollEngagementParams();
            RotationMath.seedOvershoot(ballisticPower, ballisticTicks, corrDecay, corrLoops);
            inCorrectionPhase    = false;
            correctionHoldTicks  = 0;
            microPauseTicks      = 0;
            return;
        }

        // ── phase 1: reaction delay — do nothing until timer expires
        if (System.currentTimeMillis() - targetAcquiredAt < reactionDelayMs) return;

        // ── micro-pause: random hand hesitation ~6% of ticks
        if (microPauseTicks > 0) { microPauseTicks--; return; }
        if (RNG.nextFloat() < 0.06f) { microPauseTicks = 1 + RNG.nextInt(2); return; }

        float[] raw = getAnglesTo(target);
        float yD = Math.abs(RotationMath.angleDiff(raw[0], mc.thePlayer.rotationYaw));
        float pD = Math.abs(RotationMath.angleDiff(raw[1], mc.thePlayer.rotationPitch));

        // ── phase 4 + 5: crosshair in deadzone — tremor + damped corrections
        if (yD < deadzoneAngle && pD < deadzoneAngle * 0.7f) {

            // phase 5: pending correction ticks
            if (inCorrectionPhase) {
                if (correctionHoldTicks > 0) {
                    correctionHoldTicks--;
                } else if (RotationMath.hasCorrections()) {
                    float[] corr = RotationMath.correctStep();
                    mc.thePlayer.rotationYaw   += corr[0];
                    mc.thePlayer.rotationPitch += corr[1];
                    correctionHoldTicks = 1 + RNG.nextInt(2); // 1-2 tick gap between corrections
                } else {
                    inCorrectionPhase = false;
                }
            }

            // phase 4: OU muscle tremor always active in deadzone
            float[] ou = RotationMath.ouStep();
            mc.thePlayer.rotationYaw   += ou[0];
            mc.thePlayer.rotationPitch += ou[1];

            tryAttack(target);
            return;
        }

        float mY, mP;

        // ── phase 2: ballistic sweep — arm throw, fast and coarse
        if (RotationMath.isBallistic()) {
            float[] bResult = RotationMath.ballisticStep(
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch,
                raw[0], raw[1]
            );
            mY = bResult[0];
            mP = bResult[1];
            // no overshoot burn yet — still throwing
        } else {
            // ── phase 3: bezier wrist deceleration
            mY = RotationMath.bezierStep(mc.thePlayer.rotationYaw,   raw[0], smoothSpeed);
            mP = RotationMath.bezierStep(mc.thePlayer.rotationPitch, raw[1], smoothSpeed * 0.85f);

            // phase 5: entering near-zone — burn first overshoot, arm remaining corrections
            float nYD = Math.abs(RotationMath.angleDiff(raw[0], mY));
            if (!inCorrectionPhase && nYD < deadzoneAngle * 1.8f) {
                if (RNG.nextFloat() < 0.72f) { // not every approach overshoots
                    mY = RotationMath.burnOvershootYaw(mY);
                    mP = RotationMath.burnOvershootPitch(mP);
                    inCorrectionPhase   = true;
                    correctionHoldTicks = 1 + RNG.nextInt(2);
                }
            }
        }

        // ── jitter, clamp, GCD snap — applied every tick regardless of phase
        float[] j = RotationMath.injectJitter(mY, mP);
        float[] c = RotationMath.clampDelta(j[0], j[1],
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        float[] g = GCDFix.apply(c[0], c[1],
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);

        mc.thePlayer.rotationYaw   = g[0];
        mc.thePlayer.rotationPitch = Math.max(-90f, Math.min(90f, g[1]));

        tryAttack(target);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // tryAttack — phase 6: click timing offset, miss chance
    // ─────────────────────────────────────────────────────────────────────────
    private void tryAttack(EntityLivingBase t) {
        long now  = System.currentTimeMillis();
        long wait = attackDelayBase + (long)(RNG.nextFloat() * attackDelayVar * 2) - attackDelayVar;
        if (RNG.nextFloat() < missChance)  { lastAttack = now; return; } // phase 6 miss
        if (now - lastAttack < wait)       return;
        if (!HitValidation.canHit(mc.thePlayer, t,
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, 0.12f)) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, t);
        lastAttack = now;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findTarget — closest in-FOV living player, occasional second-pick
    // ─────────────────────────────────────────────────────────────────────────
    private EntityLivingBase findTarget() {
        List<EntityLivingBase> c = mc.theWorld.getLoadedEntityList().stream()
            .filter(e -> e instanceof EntityPlayer)
            .map(e -> (EntityLivingBase) e)
            .filter(t -> t != mc.thePlayer)
            .filter(t -> !t.isDead && t.getHealth() > 0)
            .filter(t -> mc.thePlayer.getDistanceSqToEntity(t) <= maxReach * maxReach * 1.5)
            .filter(this::isInFOV)
            .collect(Collectors.toList());
        if (c.isEmpty()) return null;
        c.sort(Comparator.comparingDouble(t -> mc.thePlayer.getDistanceSqToEntity(t)));
        if (c.size() > 1 && RNG.nextFloat() < 0.12f) return c.get(1);
        return c.get(0);
    }

    private boolean isInFOV(EntityLivingBase t) {
        float[] a = getAnglesTo(t);
        return Math.abs(RotationMath.angleDiff(a[0], mc.thePlayer.rotationYaw))   <= fovFilter / 2f
            && Math.abs(RotationMath.angleDiff(a[1], mc.thePlayer.rotationPitch)) <= fovFilter / 2f;
    }

    private float[] getAnglesTo(EntityLivingBase t) {
        double dx   = t.posX - mc.thePlayer.posX;
        double dy   = (t.posY + t.getEyeHeight() * eyeOffsetMul)
                    - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz   = t.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
            (float)(-Math.toDegrees(Math.atan2(dy, dist)))
        };
    }

    public void toggle() {
        enabled = !enabled;
        rollEngagementParams();
        RotationMath.resetOU();
        AimbotMod.log.info("[LilBig] aimbot " + (enabled ? "ON" : "OFF"));
    }

    public EntityLivingBase getCurrentTarget() { return currentTarget; }
}
