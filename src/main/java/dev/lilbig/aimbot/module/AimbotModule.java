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
import java.util.Random;

public class AimbotModule {
    public  boolean enabled         = false;
    private float   smoothSpeed     = 0.35f;
    private float   fovFilter       = 180.0f;
    private float   maxReach        = 3.5f;
    private long    attackDelayBase = 560L;
    private long    attackDelayVar  = 90L;
    private float   targetYaw       = 0f;
    private float   targetPitch     = 0f;
    private long    lastAttack      = 0L;
    private final Random RNG        = new Random();
    private final Minecraft mc      = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        GCDFix.seed(mc.gameSettings.mouseSensitivity);

        if (e.phase != TickEvent.Phase.END) return;
        if (!enabled) return;

        EntityLivingBase target = findTarget();
        if (target == null) return;

        float[] raw = getAnglesTo(target);
        float smoothYaw   = RotationMath.smoothStep(mc.thePlayer.rotationYaw,   raw[0], smoothSpeed);
        float smoothPitch = RotationMath.smoothStep(mc.thePlayer.rotationPitch, raw[1], smoothSpeed * 0.9f);
        float[] clamped  = RotationMath.clampDelta(smoothYaw, smoothPitch, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        float[] gcdFixed = GCDFix.apply(clamped[0], clamped[1], mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        float[] jittered = RotationMath.injectJitter(gcdFixed[0], gcdFixed[1]);

        targetYaw   = jittered[0];
        targetPitch = Math.max(-90f, Math.min(90f, jittered[1]));
        mc.thePlayer.rotationYaw   = targetYaw;
        mc.thePlayer.rotationPitch = targetPitch;

        long now  = System.currentTimeMillis();
        long wait = attackDelayBase + (long)(RNG.nextFloat() * attackDelayVar * 2) - attackDelayVar;
        if (now - lastAttack < wait) return;
        if (!HitValidation.canHit(mc.thePlayer, target, targetYaw, targetPitch, 0.12f)) return;
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, target);
        lastAttack = now;
    }

    private EntityLivingBase findTarget() {
        return mc.theWorld.getLoadedEntityList().stream()
            .filter(e -> e instanceof EntityPlayer)
            .map(e -> (EntityLivingBase) e)
            .filter(t -> t != mc.thePlayer)
            .filter(t -> !t.isDead && t.getHealth() > 0)
            .filter(t -> mc.thePlayer.getDistanceSqToEntity(t) <= maxReach * maxReach * 1.5)
            .filter(t -> isInFOV(t))
            .min(Comparator.comparingDouble(t -> mc.thePlayer.getDistanceSqToEntity(t)))
            .orElse(null);
    }

    private boolean isInFOV(EntityLivingBase t) {
        float[] a = getAnglesTo(t);
        return Math.abs(RotationMath.angleDiff(a[0], mc.thePlayer.rotationYaw))   <= fovFilter / 2f
            && Math.abs(RotationMath.angleDiff(a[1], mc.thePlayer.rotationPitch)) <= fovFilter / 2f;
    }

    private float[] getAnglesTo(EntityLivingBase t) {
        double dx   = t.posX - mc.thePlayer.posX;
        double dy   = (t.posY + t.getEyeHeight() * 0.9) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz   = t.posZ - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx*dx + dz*dz);
        return new float[]{
            (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
            (float)(-Math.toDegrees(Math.atan2(dy, dist)))
        };
    }

    public void toggle() {
        enabled = !enabled;
        AimbotMod.log.info("[LilBig] aimbot " + (enabled ? "ON" : "OFF"));
    }
}
