package dev.lilbig.aimbot.input;

import dev.lilbig.aimbot.AimbotMod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyBindHandler {
    // 184 = RIGHT ALT raw keycode
    private static final int TOGGLE_KEY = 184;
    public void register() {}

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (Keyboard.isKeyDown(TOGGLE_KEY)) {
            AimbotMod.aimbot.toggle();
        }
    }
}
