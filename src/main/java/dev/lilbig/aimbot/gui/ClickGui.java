package dev.lilbig.aimbot.gui;

import dev.lilbig.aimbot.AimbotMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Mouse;
import java.io.IOException;

public class ClickGui extends GuiScreen {

    private int winX = 50, winY = 50;
    private int winW = 220;
    private boolean dragging  = false;
    private int dragOffX, dragOffY;
    private int activeSlider  = -1;

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void drawScreen(int mx, int my, float pt) {
        int padding = 6;
        int rowH    = 14;
        int winH    = padding * 2 + rowH * 10 + 10;

        drawRect(winX, winY, winX + winW, winY + winH, 0xCC1a1a2e);
        drawRect(winX, winY, winX + winW, winY + 13,   0xFF16213e);
        drawString(fontRendererObj, "\u00a7bLilBig \u00a77Aimbot", winX + 5, winY + 3, 0xFFFFFF);

        int y = winY + 16;

        boolean on = AimbotMod.aimbot.enabled;
        drawRect(winX + padding, y, winX + winW - padding, y + rowH,
                 on ? 0xFF0f3460 : 0xFF2d2d2d);
        drawString(fontRendererObj,
                   on ? "\u00a7aAimbot  \u00a77[ON]" : "\u00a7cAimbot  \u00a77[OFF]",
                   winX + padding + 4, y + 3, 0xFFFFFF);
        y += rowH + 4;

        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 5;

        y = drawSlider(mx, my, y, 0, "Smooth",
                       AimbotMod.aimbot.smoothSpeed, 0.10f, 0.60f, padding);
        y = drawSlider(mx, my, y, 1, "FOV   ",
                       AimbotMod.aimbot.fovFilter,   10f,   180f,  padding);
        y = drawSlider(mx, my, y, 2, "Reach ",
                       AimbotMod.aimbot.maxReach,    2.0f,  6.0f,  padding);

        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 5;

        drawString(fontRendererObj, "\u00a77Toggle: \u00a7eRIGHT ALT", winX + padding + 2, y + 2, 0xAAAAAA);
        y += rowH;
        drawString(fontRendererObj, "\u00a77GUI:    \u00a7eINSERT",     winX + padding + 2, y + 2, 0xAAAAAA);
        y += rowH + 4;

        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 5;

        EntityLivingBase tgt = AimbotMod.aimbot.currentTarget;
        if (tgt != null) {
            double dist = Math.sqrt(mc.thePlayer.getDistanceSqToEntity(tgt));
            float  hp   = tgt.getHealth();
            drawString(fontRendererObj,
                       "\u00a77Target: \u00a7f" + tgt.getName(),
                       winX + padding + 2, y + 2, 0xFFFFFF);
            y += rowH;
            drawString(fontRendererObj,
                       "\u00a77HP: \u00a7c" + String.format("%.1f", hp)
                       + "  \u00a77Dist: \u00a7e" + String.format("%.1f", dist) + "m",
                       winX + padding + 2, y + 2, 0xFFFFFF);
        } else {
            drawString(fontRendererObj, "\u00a77Target: \u00a78none",
                       winX + padding + 2, y + 2, 0xFFFFFF);
        }

        // drag window
        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                winX = mx - dragOffX;
                winY = my - dragOffY;
            } else {
                dragging = false;
            }
        }

        // drag active slider
        if (activeSlider >= 0) {
            if (Mouse.isButtonDown(0)) {
                applySliderDrag(mx, activeSlider, padding);
            } else {
                activeSlider = -1;
            }
        }

        super.drawScreen(mx, my, pt);
    }

    private int drawSlider(int mx, int my, int y, int id, String label,
                            float val, float min, float max, int padding) {
        int barX = winX + padding;
        int barW = winW - padding * 2;
        int rowH = 14;
        float pct   = (val - min) / (max - min);
        int   fillW = (int)(barW * pct);

        drawRect(barX, y, barX + barW, y + rowH, 0xFF222244);
        drawRect(barX, y, barX + fillW, y + rowH, 0xFF1a5fa8);

        String fmt = (max <= 10f)
            ? String.format("%.2f", val)
            : String.format("%.0f", val);
        drawString(fontRendererObj, "\u00a77" + label + ": \u00a7f" + fmt,
                   barX + 3, y + 3, 0xFFFFFF);

        return y + rowH + 2;
    }

    private void applySliderDrag(int mx, int id, int padding) {
        int   barX = winX + padding;
        int   barW = winW - padding * 2;
        float pct  = Math.max(0f, Math.min(1f, (float)(mx - barX) / barW));
        switch (id) {
            case 0: AimbotMod.aimbot.smoothSpeed = 0.10f + pct * 0.50f; break;
            case 1: AimbotMod.aimbot.fovFilter   = 10f   + pct * 170f;  break;
            case 2: AimbotMod.aimbot.maxReach     = 2.0f  + pct * 4.0f;  break;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        int padding     = 6;
        int rowH        = 14;
        int toggleY     = winY + 16;
        int sliderStartY = toggleY + rowH + 4 + 5;

        // toggle
        if (btn == 0
                && mx >= winX + padding && mx <= winX + winW - padding
                && my >= toggleY && my <= toggleY + rowH) {
            AimbotMod.aimbot.toggle();
            return;
        }

        // sliders
        for (int i = 0; i < 3; i++) {
            int sy = sliderStartY + i * (rowH + 2);
            if (btn == 0
                    && mx >= winX + padding && mx <= winX + winW - padding
                    && my >= sy && my <= sy + rowH) {
                activeSlider = i;
                applySliderDrag(mx, i, padding);
                return;
            }
        }

        // titlebar drag
        if (btn == 0 && my >= winY && my <= winY + 13) {
            dragging = true;
            dragOffX = mx - winX;
            dragOffY = my - winY;
            return;
        }

        super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
