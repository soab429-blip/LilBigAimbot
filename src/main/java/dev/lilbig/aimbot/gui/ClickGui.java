package dev.lilbig.aimbot.gui;

import dev.lilbig.aimbot.AimbotMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;
import java.util.Random;

public class ClickGui extends GuiScreen {

    // ── window state ─────────────────────────────────────────────────────────
    private int   winX = 50, winY = 50;
    private int   winW = 230;
    private boolean dragging   = false;
    private int   dragOffX, dragOffY;
    private int   activeSlider = -1;

    // ── raven-style open animation ────────────────────────────────────────────
    private long  openedAt    = 0L;
    private float animCache   = 0f;
    private long  animLast    = 0L;
    private float animUpdates = 400f;

    // ── chroma ticker ─────────────────────────────────────────────────────────
    private long chromaOff = 0L;

    // ── developer type-on ────────────────────────────────────────────────────
    private final String devLine = "LILBIG4DEV";

    private final Minecraft mc = Minecraft.getMinecraft();

    // ── slider table — order matches SLIDER_IDS ───────────────────────────────
    // id  label            min     max      fmt
    private static final Object[][] SLIDERS = {
        { "Smooth",         0.10f,  0.60f,  "%.2f" },  // 0
        { "FOV",            10f,    180f,   "%.0f" },  // 1
        { "Reach",          2.0f,   6.0f,   "%.1f" },  // 2
        { "Reaction ms",    80f,    300f,   "%.0f" },  // 3 — phase 1 max
        { "Ballistic mul",  1.0f,   3.0f,   "%.2f" },  // 4 — phase 2 throw power
        { "Ballistic tks",  2f,     7f,     "%.0f" },  // 5 — phase 2 duration ticks
        { "Corr decay",     0.20f,  0.75f,  "%.2f" },  // 6 — phase 5 amp decay
        { "Corr loops",     1f,     4f,     "%.0f" },  // 7 — phase 5 loop count
        { "Miss chance",    0.0f,   0.25f,  "%.2f" },  // 8 — phase 6
    };

    // ─────────────────────────────────────────────────────────────────────────

    private boolean mouseDown() {
        try { return Mouse.isButtonDown(0); } catch (Exception e) { return false; }
    }

    // easeOutQuint — mirrors raven's type=2 timer
    private float ease(float t) {
        return (float)(1.0 - Math.pow(1.0 - Math.min(t, 1f), 5.0));
    }

    private float animValue(float begin, float end) {
        float t = ease((float)(System.currentTimeMillis() - animLast) / animUpdates);
        float v = begin + t * (end - begin);
        return (end > begin) ? Math.min(v, end) : Math.max(v, end);
    }

    private int chroma(long offset) {
        float hue = ((System.currentTimeMillis() + offset) % 2000L) / 2000f;
        return Color.HSBtoRGB(hue, 0.7f, 1.0f);
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void initGui() {
        super.initGui();
        openedAt  = System.currentTimeMillis();
        animLast  = openedAt;
        animCache = 0f;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        int padding = 6;
        int rowH    = 14;
        int titleH  = 13;

        // ── animate slide-in (raven open anim style) ──────────────────────────
        float slide = animValue(0f, 1f);  // 0→1 over 400ms easeOutQuint

        // total window height: title + toggle + sep + sliders + sep + info + sep + target
        int sliderCount = SLIDERS.length;
        int winH = titleH + rowH + 5 + (sliderCount * (rowH + 2)) + 5
                 + rowH + 5 + rowH * 2 + padding + 6;

        // fade-in alpha on the whole window
        int alpha = (int)(slide * 0xCC);
        if (alpha < 10) { super.drawScreen(mx, my, pt); return; }

        // ── dark background vignette (raven darkBackground) ───────────────────
        drawRect(0, 0, width, height, (int)(slide * 0.45f * 255f) << 24);

        // ── window body ───────────────────────────────────────────────────────
        drawRect(winX, winY, winX + winW, winY + winH, (alpha << 24) | 0x1a1a2e);

        // ── title bar ─────────────────────────────────────────────────────────
        drawRect(winX, winY, winX + winW, winY + titleH, 0xFF16213e);

        // chroma "LilBig" lettering — raven watermark style
        String brand = "LilBig";
        for (int i = 0; i < brand.length(); i++) {
            int cx = winX + 5 + i * (fontRendererObj.getStringWidth("M") - 1);
            fontRendererObj.drawStringWithShadow(
                String.valueOf(brand.charAt(i)), cx, winY + 3, chroma(i * 250L));
        }
        fontRendererObj.drawStringWithShadow(
            "\u00a77 Aimbot", winX + 5 + brand.length() * 6, winY + 3, 0xAAAAAA);

        // ── dev type-on (raven devs line) ─────────────────────────────────────
        long elapsed    = System.currentTimeMillis() - openedAt;
        int  charIdx    = (int)(elapsed / 120L);
        String rendered = "";
        for (int i = 0; i < devLine.length(); i++) {
            rendered += (i < charIdx)
                ? devLine.charAt(i)
                : (char)(new Random().nextInt(26) + 'a');
        }
        if (charIdx >= devLine.length()) rendered = devLine;
        fontRendererObj.drawStringWithShadow(
            "\u00a78dev. \u00a77" + rendered,
            winX + winW - fontRendererObj.getStringWidth("dev. " + devLine) - 4,
            winY + 3, 0x888888);

        int y = winY + titleH + 2;

        // ── toggle button — raven ModuleComponent style ───────────────────────
        boolean on = AimbotMod.aimbot.enabled;
        int toggleBg = on ? 0xFF0f3460 : 0xFF2a2a2a;
        drawRect(winX + padding, y, winX + winW - padding, y + rowH, toggleBg);
        // left accent bar when enabled
        if (on) drawRect(winX + padding, y, winX + padding + 2, y + rowH, chroma(0));
        String toggleLabel = on ? "\u00a7aAimbot\u00a77  ON" : "\u00a7cAimbot\u00a77  OFF";
        fontRendererObj.drawStringWithShadow(toggleLabel, winX + padding + 6, y + 3, 0xFFFFFF);
        y += rowH + 2;

        // ── separator ─────────────────────────────────────────────────────────
        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 4;

        // ── sliders ───────────────────────────────────────────────────────────
        for (int i = 0; i < sliderCount; i++) {
            y = drawSlider(mx, y, i, padding, rowH);
        }

        // ── separator ─────────────────────────────────────────────────────────
        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 4;

        // ── key hints ─────────────────────────────────────────────────────────
        fontRendererObj.drawStringWithShadow(
            "\u00a77Toggle: \u00a7eRIGHT ALT", winX + padding + 2, y + 2, 0x888888);
        y += rowH;

        // ── separator ─────────────────────────────────────────────────────────
        drawRect(winX + padding, y, winX + winW - padding, y + 1, 0xFF333355);
        y += 4;

        // ── target info ───────────────────────────────────────────────────────
        EntityLivingBase tgt = AimbotMod.aimbot.currentTarget;
        if (tgt != null && mc.thePlayer != null) {
            double dist = Math.sqrt(mc.thePlayer.getDistanceSqToEntity(tgt));
            float  hp   = tgt.getHealth();
            fontRendererObj.drawStringWithShadow(
                "\u00a77Target: \u00a7f" + tgt.getName(),
                winX + padding + 2, y + 2, 0xFFFFFF);
            y += rowH;
            // hp bar
            int barW  = winW - padding * 2;
            int hpFill = (int)(barW * (hp / 20f));
            drawRect(winX + padding, y, winX + padding + barW, y + 3, 0xFF2a0a0a);
            drawRect(winX + padding, y, winX + padding + hpFill, y + 3,
                     hp > 10 ? 0xFF22bb22 : 0xFFbb2222);
            y += 5;
            fontRendererObj.drawStringWithShadow(
                "\u00a7cHP \u00a7f" + String.format("%.1f", hp)
                + "\u00a77  dist \u00a7e" + String.format("%.1f", dist) + "m",
                winX + padding + 2, y + 2, 0xFFFFFF);
        } else {
            fontRendererObj.drawStringWithShadow(
                "\u00a77Target: \u00a78none", winX + padding + 2, y + 2, 0xFFFFFF);
        }

        // ── drag / slider mouse handling ──────────────────────────────────────
        boolean down = mouseDown();
        if (dragging) {
            if (down) { winX = mx - dragOffX; winY = my - dragOffY; }
            else        dragging = false;
        }
        if (activeSlider >= 0) {
            if (down) applySliderDrag(mx, activeSlider, padding);
            else       activeSlider = -1;
        }

        super.drawScreen(mx, my, pt);
    }

    // ── drawSlider ────────────────────────────────────────────────────────────
    private int drawSlider(int mx, int y, int id, int padding, int rowH) {
        String label = (String)  SLIDERS[id][0];
        float  min   = (float)   SLIDERS[id][1];
        float  max   = (float)   SLIDERS[id][2];
        String fmt   = (String)  SLIDERS[id][3];
        float  val   = getSliderVal(id);

        int barX  = winX + padding;
        int barW  = winW - padding * 2;
        int fillW = (int)(barW * ((val - min) / (max - min)));

        // track
        drawRect(barX, y, barX + barW, y + rowH, 0xFF1e1e3a);
        // fill — chroma accent matching raven's enabled color
        drawRect(barX, y, barX + fillW, y + rowH, 0xFF0f3460);
        // left chroma pip
        drawRect(barX + fillW - 1, y, barX + fillW + 1, y + rowH, chroma(id * 180L));

        fontRendererObj.drawStringWithShadow(
            "\u00a77" + label + ": \u00a7f" + String.format(fmt, val),
            barX + 3, y + 3, 0xFFFFFF);

        return y + rowH + 2;
    }

    // ── value getters — read live from module/RotationMath ───────────────────
    private float getSliderVal(int id) {
        switch (id) {
            case 0: return AimbotMod.aimbot.smoothSpeed;
            case 1: return AimbotMod.aimbot.fovFilter;
            case 2: return AimbotMod.aimbot.maxReach;
            case 3: return AimbotMod.aimbot.reactionDelayMax;
            case 4: return AimbotMod.aimbot.ballisticPower;
            case 5: return AimbotMod.aimbot.ballisticTicks;
            case 6: return AimbotMod.aimbot.corrDecay;
            case 7: return AimbotMod.aimbot.corrLoops;
            case 8: return AimbotMod.aimbot.missChance;
            default: return 0f;
        }
    }

    // ── applySliderDrag ───────────────────────────────────────────────────────
    private void applySliderDrag(int mx, int id, int padding) {
        int   barX = winX + padding;
        int   barW = winW - padding * 2;
        float pct  = Math.max(0f, Math.min(1f, (float)(mx - barX) / barW));
        float min  = (float) SLIDERS[id][1];
        float max  = (float) SLIDERS[id][2];
        float val  = min + pct * (max - min);
        switch (id) {
            case 0: AimbotMod.aimbot.smoothSpeed      = val; break;
            case 1: AimbotMod.aimbot.fovFilter         = val; break;
            case 2: AimbotMod.aimbot.maxReach           = val; break;
            case 3: AimbotMod.aimbot.reactionDelayMax   = (long) val; break;
            case 4: AimbotMod.aimbot.ballisticPower     = val; break;
            case 5: AimbotMod.aimbot.ballisticTicks     = (int) val; break;
            case 6: AimbotMod.aimbot.corrDecay          = val; break;
            case 7: AimbotMod.aimbot.corrLoops          = (int) val; break;
            case 8: AimbotMod.aimbot.missChance         = val; break;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int btn) throws IOException {
        int padding      = 6;
        int rowH         = 14;
        int titleH       = 13;
        int toggleY      = winY + titleH + 2;
        int sliderStartY = toggleY + rowH + 2 + 4;

        // toggle
        if (btn == 0
                && mx >= winX + padding && mx <= winX + winW - padding
                && my >= toggleY        && my <= toggleY + rowH) {
            AimbotMod.aimbot.toggle();
            return;
        }

        // sliders
        for (int i = 0; i < SLIDERS.length; i++) {
            int sy = sliderStartY + i * (rowH + 2);
            if (btn == 0
                    && mx >= winX + padding && mx <= winX + winW - padding
                    && my >= sy             && my <= sy + rowH) {
                activeSlider = i;
                applySliderDrag(mx, i, padding);
                return;
            }
        }

        // title drag
        if (btn == 0 && my >= winY && my <= winY + titleH) {
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
