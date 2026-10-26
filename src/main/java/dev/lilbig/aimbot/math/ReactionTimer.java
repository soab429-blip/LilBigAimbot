package dev.lilbig.aimbot.math;
import java.util.Random;
public final class ReactionTimer {
    private static final Random RNG=new Random();
    private static final double LN_MU=5.10,LN_SIGMA=0.28;
    private long delayMs=0L,startMs=-1L;
    private boolean armed=false;
    public void arm(){double s=Math.exp(LN_MU+LN_SIGMA*RNG.nextGaussian());delayMs=(long)Math.max(80,Math.min(320,s));startMs=System.currentTimeMillis();armed=true;}
    public boolean ready(){if(!armed)return true;if(System.currentTimeMillis()-startMs>=delayMs){armed=false;return true;}return false;}
    public void reset(){armed=false;startMs=-1L;}
}
