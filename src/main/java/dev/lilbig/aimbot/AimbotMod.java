package dev.lilbig.aimbot;

import dev.lilbig.aimbot.module.AimbotModule;
import dev.lilbig.aimbot.input.KeyBindHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

@Mod(modid = AimbotMod.MODID, version = AimbotMod.VERSION, clientSideOnly = true)
public class AimbotMod {
    public static final String MODID   = "lilbigaimbot";
    public static final String VERSION = "1.0.0";
    public static Minecraft      mc;
    public static AimbotModule   aimbot;
    public static KeyBindHandler keys;
    public static Logger         log;

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        log = e.getModLog();
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        mc     = Minecraft.getMinecraft();
        aimbot = new AimbotModule();
        keys   = new KeyBindHandler();
        keys.register();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(aimbot);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(keys);
        log.info("[LilBig] aimbot loaded");
    }
}
