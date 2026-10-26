package dev.lilbig.aimbot.math;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public final class HitValidation {
    private HitValidation() {}

    public static boolean canHit(EntityPlayerSP self, Entity target,
                                   float yaw, float pitch, float expand) {
        double eyeX   = self.posX;
        double eyeY   = self.posY + self.getEyeHeight();
        double eyeZ   = self.posZ;
        double yawRad = Math.toRadians(-yaw - 90.0);
        double pitRad = Math.toRadians(-pitch);
        double cosP   = Math.cos(pitRad);
        double dx = cosP * Math.cos(yawRad);
        double dy = Math.sin(pitRad);
        double dz = cosP * Math.sin(yawRad);
        double len = 4.5;
        Vec3 start = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 end   = new Vec3(eyeX + dx*len, eyeY + dy*len, eyeZ + dz*len);
        AxisAlignedBB bb = target.getEntityBoundingBox().expand(expand, expand, expand);
        return bb.calculateIntercept(start, end) != null;
    }
}
