package dev.lilbig.aimbot.module;

import dev.lilbig.aimbot.AimbotMod;
import dev.lilbig.aimbot.math.GCDFix;
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
    public  float            fovFilter     = 180f;
    public  float            maxReach      = 3.5f;
    public  float            smoothSpeed   = 0.28f;
    public  float            deadzoneAngle = 0.8f;
    public  float            eyeOffsetMul  = 0.9f;
    public  long             reactionDelayMax = 200L;
    public  float            ballisticPower   = 1.9f;
    public  int              ballisticTicks   = 4;
    public  float            corrDecay        = 0.42f;
    public  int              corrLoops        = 2;

    private final Random     RNG = new Random();
    private final Minecraft  mc  = Minecraft.getMinecraft();

    public  EntityLivingBase currentTarget    = null;
    private EntityLivingBase lastTarget       = null;

    // phase 1
    private long  targetAcquiredAt = 0L;

    // micro pause
    private int   microPauseTicks  = 0;

    // phase 5
    private boolean inCorrectionPhase   = false;
    private int     correctionHoldTicks = 0;

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

        // new target — arm reaction + ballistic, nothing else changes
        if (target != lastTarget) {
            lastTarget          = target;
            targetAcquiredAt    = System.currentTimeMillis();
            RotationMath.seedOvershoot(ballisticPower, ballisticTicks, corrDecay, corrLoops);
            inCorrectionPhase   = false;
            correctionHoldTicks = 0;
            microPauseTicks     = 0;
            return;
        }

        // phase 1 — reaction delay
        if (System.currentTimeMillis() - targetAcquiredAt < reactionDelayMax) return;

        // micro pause
        if (microPauseTicks > 0) { microPauseTicks--; return; }
        if (RNG.nextFloat() < 0.06f) { microPauseTicks = 1 + RNG.nextInt(2); return; }

        float[] raw = getAnglesTo(target);
        float yD = Math.abs(RotationMath.angleDiff(raw[0], mc.thePlayer.rotationYaw));
        float pD = Math.abs(RotationMath.angleDiff(raw[1], mc.thePlayer.rotationPitch));

        // phase 4 + 5 — deadzone: tremor + corrections only, no click
        if (yD < deadzoneAngle && pD < deadzoneAngle * 0.7f) {
            if (inCorrectionPhase) {
                if (correctionHoldTicks > 0) {
                    correctionHoldTicks--;
                } else if (RotationMath.hasCorrections()) {
                    float[] corr = RotationMath.correctStep();
                    mc.thePlayer.rotationYaw   += corr[0];
                    mc.thePlayer.rotationPitch += corr[1];
                    correctionHoldTicks = 1 + RNG.nextInt(2);
                } else {
                    inCorrectionPhase = false;
                }
            }
            float[] ou = RotationMath.ouStep();
            mc.thePlayer.rotationYaw   += ou[0];
            mc.thePlayer.rotationPitch += ou[1];
            return;
        }

        float mY, mP;

        // phase 2 — ballistic
        if (RotationMath.isBallistic()) {
            float[] b = RotationMath.ballisticStep(
                mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, raw[0], raw[1]);
            mY = b[0];
            mP = b[1];
        } else {
            // phase 3 — bezier wrist decel
            mY = RotationMath.bezierStep(mc.thePlayer.rotationYaw,   raw[0], smoothSpeed);
            mP = RotationMath.bezierStep(mc.thePlayer.rotationPitch, raw[1], smoothSpeed * 0.85f);

            // phase 5 — near-zone: seed first overshoot
            float nYD = Math.abs(RotationMath.angleDiff(raw[0], mY));
            if (!inCorrectionPhase && nYD < deadzoneAngle * 1.8f) {
                if (RNG.nextFloat() < 0.72f) {
                    mY = RotationMath.burnOvershootYaw(mY);
                    mP = RotationMath.burnOvershootPitch(mP);
                    inCorrectionPhase   = true;
                    correctionHoldTicks = 1 + RNG.nextInt(2);
                }
            }
        }

        float[] j = RotationMath.injectJitter(mY, mP);
        float[] c = RotationMath.clampDelta(j[0], j[1],
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        float[] g = GCDFix.apply(c[0], c[1],
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);

        mc.thePlayer.rotationYaw   = g[0];
        mc.thePlayer.rotationPitch = Math.max(-90f, Math.min(90f, g[1]));
    }

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
        RotationMath.resetOU();
        AimbotMod.log.info("[LilBig] aimbot " + (enabled ? "ON" : "OFF"));
    }
}
