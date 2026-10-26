package dev.lilbig.aimbot.gui;

import dev.lilbig.aimbot.AimbotMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.EntityLivingBase;
import java.io.IOException;

public class ClickGui extends GuiScreen {

    private int winX = 50, winY = 50;
    private int winW = 220;
    private boolean dragging  = false;
    private int dragOffX, dragOffY;
    private int activeSlider  = -1; // 0=smooth 1=fov 2=reach

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void drawScreen(int mx, int my, float pt) {
        int padding = 6;
        int rowH    = 14;
        int winH    = padding * 2 + rowH * 10 + 10;

        // background + titlebar
        drawRect(winX, winY, winX + winW, winY + winH, 0xCC1a1a2e);
        drawRect(winX, winY, winX + winW, winY + 13,   0xFF16213e);
        drawString(fontRendererObj, "\u00a7bLilBig \u00a77Aimbot", winX + 5, winY + 3, 0xFFFFFF);

        int y = winY + 16;

        // toggle button
        boolean on = AimbotMod.aimbot.enabled;
        drawRect(winX + padding, y, winX + winW - padding, y + rowH,
                 on ? 0xFF0f3460 : 0xFF2d2d2d);
        drawString(fontRendererObj,
                   on ? "\u00a7aAimbot  \u00a77[ON]" : "\u00a7cAimbot  \u00a77[OFF]",
                   winX + padding + 4, y + 3, 0xFFFFFF);
        y += rowH + 4;

        // divider
        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 5;

        // sliders
        y = drawSlider(mx, y, 0, "Smooth",
                       AimbotMod.aimbot.smoothSpeed, 0.10f, 0.60f, padding);
        y = drawSlider(mx, y, 1, "FOV   ",
                       AimbotMod.aimbot.fovFilter,   10f,   180f,  padding);
        y = drawSlider(mx, y, 2, "Reach ",
                       AimbotMod.aimbot.maxReach,    2.0f,  6.0f,  padding);

        // divider
        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 5;

        // keybind display
        drawString(fontRendererObj, "\u00a77Toggle: \u00a7eRIGHT ALT", winX + padding + 2, y + 2,  0xAAAAAA);
        y += rowH;
        drawString(fontRendererObj, "\u00a77GUI:    \u00a7eINSERT",     winX + padding + 2, y + 2,  0xAAAAAA);
        y += rowH + 4;

        // divider
        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 5;

        // target info
        EntityLivingBase tgt = AimbotMod.aimbot.currentTarget;
        if (tgt != null) {
            double dist = mc.thePlayer.getDistanceSqToEntity(tgt);
            dist = Math.sqrt(dist);
            float hp    = tgt.getHealth();
            String name = tgt.getName();
            drawString(fontRendererObj,
                       "\u00a77Target: \u00a7f" + name,
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

        // handle active slider drag
        if (activeSlider >= 0 && Mouse.isButtonDown(0)) {
            applySliderDrag(mx, activeSlider, padding);
        } else {
            activeSlider = -1;
        }

        // handle window drag
        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                winX = mx - dragOffX;
                winY = my - dragOffY;
            } else {
                dragging = false;
            }
        }

        super.drawScreen(mx, my, pt);
    }

    // draws one slider row, returns next Y
    private int drawSlider(int mx, int y, int id, String label,
                            float val, float min, float max, int padding) {
        int barX = winX + padding;
        int barW = winW - padding * 2;
        int rowH = 14;

        float pct    = (val - min) / (max - min);
        int   fillW  = (int)(barW * pct);

        // track
        drawRect(barX, y, barX + barW, y + rowH, 0xFF222244);
        // fill
        drawRect(barX, y, barX + fillW, y + rowH, 0xFF1a5fa8);
        // label + value
        String fmt = (max <= 10f)
            ? String.format("%.2f", val)
            : String.format("%.0f", val);
        drawString(fontRendererObj, "\u00a77" + label + ": \u00a7f" + fmt,
                   barX + 3, y + 3, 0xFFFFFF);

        // hit-test for drag start
        if (Mouse.isButtonDown(0) && mx >= barX && mx <= barX + barW
                && activeSlider == -1) {
            // check Y too — only trigger if mouse is on this row
            // (we don't have my here; handled in mouseClicked)
        }

        return y + rowH + 2;
    }

    private void applySliderDrag(int mx, int id, int padding) {
        int barX = winX + padding;
        int barW = winW - padding * 2;
        float pct = Math.max(0f, Math.min(1f, (float)(mx - barX) / barW));
        switch (id) {
            case 0: AimbotMod.aimbot.smoothSpeed = 0.10f + pct * 0.50f; break;
            case 1: AimbotMod.aimbot.fovFilter   = 10f   + pct * 170f;  break;
            case 2: AimbotMod.aimbot.maxReach     = 2.0f  + pct * 4.0f;  break;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        int padding = 6;
        int rowH    = 14;
        int toggleY = winY + 16;

        // toggle click
        if (btn == 0 && mx >= winX + padding && mx <= winX + winW - padding
                && my >= toggleY && my <= toggleY + rowH) {
            AimbotMod.aimbot.toggle();
            return;
        }

        // slider click — find which row
        int sliderStartY = toggleY + rowH + 4 + 5; // after toggle + divider
        for (int i = 0; i < 3; i++) {
            int sy = sliderStartY + i * (rowH + 2);
            if (btn == 0 && mx >= winX + padding && mx <= winX + winW - padding
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
