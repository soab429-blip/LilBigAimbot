package dev.lilbig.aimbot.module;
import dev.lilbig.aimbot.AimbotMod;
import dev.lilbig.aimbot.math.GCDFix;
import dev.lilbig.aimbot.math.HitValidation;
import dev.lilbig.aimbot.math.ReactionTimer;
import dev.lilbig.aimbot.math.RotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.*;import java.util.stream.*;
public class AimbotModule {
    public boolean enabled=false;
    public float fovFilter=180f,maxReach=3.5f;
    private float smoothSpeed,deadzoneAngle,missChance,eyeOffsetMul;
    private long attackDelayBase,attackDelayVar,lastAttack=0L;
    private final Random RNG=new Random();
    private final Minecraft mc=Minecraft.getMinecraft();
    private final ReactionTimer reactionTimer=new ReactionTimer();
    public EntityLivingBase currentTarget=null;
    private EntityLivingBase lastTarget=null;
    private int microPauseTicks=0,correctionTicks=0;
    private boolean inCorrection=false;
    public AimbotModule(){rollEngagementParams();}
    private void rollEngagementParams(){smoothSpeed=0.22f+RNG.nextFloat()*0.18f;deadzoneAngle=0.45f+RNG.nextFloat()*0.55f;attackDelayBase=480L+(long)(RNG.nextFloat()*120L);attackDelayVar=80L+(long)(RNG.nextFloat()*60L);missChance=0.06f+RNG.nextFloat()*0.08f;eyeOffsetMul=0.65f+RNG.nextFloat()*0.35f;}
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e){
        if(mc.theWorld==null||mc.thePlayer==null)return;
        GCDFix.seed(mc.gameSettings.mouseSensitivity);
        if(e.phase!=TickEvent.Phase.END)return;
        if(!enabled){currentTarget=null;return;}
        EntityLivingBase target=findTarget();
        currentTarget=target;
        if(target==null){lastTarget=null;RotationMath.resetOU();reactionTimer.reset();return;}
        if(target!=lastTarget){lastTarget=target;rollEngagementParams();reactionTimer.arm();RotationMath.seedOvershoot();inCorrection=false;correctionTicks=0;microPauseTicks=0;return;}
        if(!reactionTimer.ready())return;
        if(microPauseTicks>0){microPauseTicks--;return;}
        if(RNG.nextFloat()<0.06f){microPauseTicks=1+RNG.nextInt(2);return;}
        float[] raw=getAnglesTo(target);
        float yD=Math.abs(RotationMath.angleDiff(raw[0],mc.thePlayer.rotationYaw));
        float pD=Math.abs(RotationMath.angleDiff(raw[1],mc.thePlayer.rotationPitch));
        if(yD<deadzoneAngle&&pD<deadzoneAngle*0.7f){
            if(inCorrection){correctionTicks--;if(correctionTicks<=0)inCorrection=false;}
            float[] ou=RotationMath.ouStep();mc.thePlayer.rotationYaw+=ou[0];mc.thePlayer.rotationPitch+=ou[1];
            tryAttack(target);return;
        }
        float mY=RotationMath.bezierStep(mc.thePlayer.rotationYaw,raw[0],smoothSpeed);
        float mP=RotationMath.bezierStep(mc.thePlayer.rotationPitch,raw[1],smoothSpeed*0.85f);
        mY=RotationMath.burnOvershootYaw(mY);
        mP=RotationMath.burnOvershootPitch(mP);
        float nYD=Math.abs(RotationMath.angleDiff(raw[0],mY));
        if(!inCorrection&&nYD<deadzoneAngle*1.8f){inCorrection=RNG.nextFloat()<0.55f;correctionTicks=2+RNG.nextInt(3);}
        float[] j=RotationMath.injectJitter(mY,mP);
        float[] c=RotationMath.clampDelta(j[0],j[1],mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);
        float[] g=GCDFix.apply(c[0],c[1],mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch);
        mc.thePlayer.rotationYaw=g[0];
        mc.thePlayer.rotationPitch=Math.max(-90f,Math.min(90f,g[1]));
        tryAttack(target);
    }
    private void tryAttack(EntityLivingBase t){
        long now=System.currentTimeMillis();
        long wait=attackDelayBase+(long)(RNG.nextFloat()*attackDelayVar*2)-attackDelayVar;
        if(RNG.nextFloat()<missChance){lastAttack=now;return;}
        if(now-lastAttack<wait)return;
        if(!HitValidation.canHit(mc.thePlayer,t,mc.thePlayer.rotationYaw,mc.thePlayer.rotationPitch,0.12f))return;
        mc.thePlayer.swingItem();mc.playerController.attackEntity(mc.thePlayer,t);lastAttack=now;
    }
    private EntityLivingBase findTarget(){
        List<EntityLivingBase> c=mc.theWorld.getLoadedEntityList().stream().filter(e->e instanceof EntityPlayer).map(e->(EntityLivingBase)e).filter(t->t!=mc.thePlayer).filter(t->!t.isDead&&t.getHealth()>0).filter(t->mc.thePlayer.getDistanceSqToEntity(t)<=maxReach*maxReach*1.5).filter(this::isInFOV).collect(Collectors.toList());
        if(c.isEmpty())return null;
        c.sort(Comparator.comparingDouble(t->mc.thePlayer.getDistanceSqToEntity(t)));
        if(c.size()>1&&RNG.nextFloat()<0.12f)return c.get(1);
        return c.get(0);
    }
    private boolean isInFOV(EntityLivingBase t){float[] a=getAnglesTo(t);return Math.abs(RotationMath.angleDiff(a[0],mc.thePlayer.rotationYaw))<=fovFilter/2f&&Math.abs(RotationMath.angleDiff(a[1],mc.thePlayer.rotationPitch))<=fovFilter/2f;}
    private float[] getAnglesTo(EntityLivingBase t){double dx=t.posX-mc.thePlayer.posX;double dy=(t.posY+t.getEyeHeight()*eyeOffsetMul)-(mc.thePlayer.posY+mc.thePlayer.getEyeHeight());double dz=t.posZ-mc.thePlayer.posZ;double dist=Math.sqrt(dx*dx+dz*dz);return new float[]{(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0),(float)(-Math.toDegrees(Math.atan2(dy,dist)))};}
    public void toggle(){enabled=!enabled;rollEngagementParams();RotationMath.resetOU();AimbotMod.log.info("[LilBig] aimbot "+(enabled?"ON":"OFF"));}
}
