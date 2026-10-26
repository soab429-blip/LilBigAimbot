package dev.lilbig.aimbot.input;

import dev.lilbig.aimbot.AimbotMod;
import dev.lilbig.aimbot.gui.ClickGui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyBindHandler {
    private static final int TOGGLE_KEY = 184; // RIGHT ALT
    private static final int GUI_KEY    = 210; // INSERT

    public void register() {}

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Keyboard.isKeyDown(TOGGLE_KEY)) {
            AimbotMod.aimbot.toggle();
        }
        if (Keyboard.isKeyDown(GUI_KEY)) {
            Minecraft.getMinecraft().displayGuiScreen(new ClickGui());
        }
    }
}
