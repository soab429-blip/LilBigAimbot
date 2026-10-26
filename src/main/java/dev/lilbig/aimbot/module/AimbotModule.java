// language: Java 8, file: AimbotModule.java, target: MC 1.8.9 Forge, Polar SaaS anticheat
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
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class AimbotModule {
    public  boolean enabled        = false;
    public  float   smoothSpeed    = 0.30f + new Random().nextFloat() * 0.12f;
    public  float   fovFilter      = 180.0f;
    public  float   maxReach       = 3.5f;
    private long    attackDelayBase = 540L;
    private long    attackDelayVar  = 110L;
    private float   targetYaw      = 0f;
    private float   targetPitch    = 0f;
    private long    lastAttack     = 0L;
    private final Random RNG       = new Random();
    private final Minecraft mc     = Minecraft.getMinecraft();
    public  EntityLivingBase currentTarget = null;
    private EntityLivingBase lastTarget    = null;
    private float            eyeOffsetMul  = randomEyeOffset();
    private int              missDelayTicks = 0;

    private float randomEyeOffset() {
        return 0.70f + RNG.nextFloat() * 0.30f;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        GCDFix.seed(mc.gameSettings.mouseSensitivity);
        if (e.phase != TickEvent.Phase.END) return;
        if (!enabled) { currentTarget = null; return; }

        EntityLivingBase target = findTarget();
        currentTarget = target;
        if (target == null) { lastTarget = null; return; }

        if (target != lastTarget) {
            lastTarget     = target;
            eyeOffsetMul   = randomEyeOffset();
            smoothSpeed    = 0.28f + RNG.nextFloat() * 0.15f;
            missDelayTicks = 1 + RNG.nextInt(3);
        }

        if (missDelayTicks > 0) { missDelayTicks--; return; }

        float[] raw       = getAnglesTo(target);
        float smoothYaw   = RotationMath.smoothStep(mc.thePlayer.rotationYaw,   raw[0], smoothSpeed);
        float smoothPitch = RotationMath.smoothStep(mc.thePlayer.rotationPitch, raw[1], smoothSpeed * 0.88f);
        float[] clamped   = RotationMath.clampDelta(smoothYaw, smoothPitch, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        float[] gcdFixed  = GCDFix.apply(clamped[0], clamped[1], mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        float[] jittered  = RotationMath.injectJitter(gcdFixed[0], gcdFixed[1]);

        targetYaw   = jittered[0];
        targetPitch = Math.max(-90f, Math.min(90f, jittered[1]));
        mc.thePlayer.rotationYaw   = targetYaw;
        mc.thePlayer.rotationPitch = targetPitch;

        long now  = System.currentTimeMillis();
        long wait = attackDelayBase + (long)(RNG.nextFloat() * attackDelayVar * 2) - attackDelayVar;
        if (RNG.nextFloat() < 0.08f) { lastAttack = now; return; }
        if (now - lastAttack < wait) return;
        if (RNG.nextFloat() < 0.30f) return;
        if (!HitValidation.canHit(mc.thePlayer, target, targetYaw, targetPitch, 0.12f)) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        lastAttack = now;
    }

    private EntityLivingBase findTarget() {
        List<EntityLivingBase> candidates = mc.theWorld.getLoadedEntityList().stream()
            .filter(e -> e instanceof EntityPlayer)
            .map(e -> (EntityLivingBase) e)
            .filter(t -> t != mc.thePlayer)
            .filter(t -> !t.isDead && t.getHealth() > 0)
            .filter(t -> mc.thePlayer.getDistanceSqToEntity(t) <= maxReach * maxReach * 1.5)
            .filter(this::isInFOV)
            .collect(Collectors.toList());
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(t -> mc.thePlayer.getDistanceSqToEntity(t)));
        if (candidates.size() > 1 && RNG.nextFloat() < 0.12f) return candidates.get(1);
        return candidates.get(0);
    }

    private boolean isInFOV(EntityLivingBase t) {
        float[] a = getAnglesTo(t);
        return Math.abs(RotationMath.angleDiff(a[0], mc.thePlayer.rotationYaw))   <= fovFilter / 2f
            && Math.abs(RotationMath.angleDiff(a[1], mc.thePlayer.rotationPitch)) <= fovFilter / 2f;
    }

    private float[] getAnglesTo(EntityLivingBase t) {
        double dx   = t.posX - mc.thePlayer.posX;
        double dy   = (t.posY + t.getEyeHeight() * eyeOffsetMul) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz   = t.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx*dx + dz*dz);
        return new float[]{
            (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
            (float)(-Math.toDegrees(Math.atan2(dy, dist)))
        };
    }

    public void toggle() {
        enabled     = !enabled;
        smoothSpeed = 0.28f + RNG.nextFloat() * 0.15f;
        AimbotMod.log.info("[LilBig] aimbot " + (enabled ? "ON" : "OFF"));
    }
}
