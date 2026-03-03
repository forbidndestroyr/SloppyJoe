package com.sloppyjoe.client;

import com.sloppyjoe.client.cutscene.HollowPurpleCutscene;
import com.sloppyjoe.client.cutscene.HollowPurpleCutscene.Phase;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;

/**
 * HUD renderer for the Hollow Purple cutscene.
 *
 * HP_CHARGE  — dark energy crackles (blue left, red right) + cinematic bars
 * HP_BLUE    — cinematic bars only (world-space blue cube visible in game world)
 * HP_RED     — cinematic bars only (world-space red cube visible in game world)
 * HP_COMBINE — cinematic bars + purple energy beam connecting hand positions
 * HP_PURPLE  — cinematic bars + purple radiance (cubes merged in world space)
 * HP_FIRE    — white blinding flash
 *
 * The 3D world-space glowing cubes are rendered by the WorldRenderEvents callback
 * in SloppyJoeModClient. This renderer only handles 2D HUD overlay effects.
 */
@Environment(EnvType.CLIENT)
public class HollowPurpleRenderer {

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!HollowPurpleCutscene.isActive()) return;

            Phase phase    = HollowPurpleCutscene.getPhase();
            float progress = HollowPurpleCutscene.getPhaseProgress();
            int   timer    = HollowPurpleCutscene.getPhaseTimer();
            int   sw       = ctx.getScaledWindowWidth();
            int   sh       = ctx.getScaledWindowHeight();
            int   cx       = sw / 2;
            int   cy       = sh / 2;

            switch (phase) {
                case HP_CHARGE  -> renderCharge(ctx, sw, sh, cx, cy, timer, progress);
                case HP_BLUE    -> renderBars(ctx, sw, sh, 1f);
                case HP_RED     -> renderBars(ctx, sw, sh, 1f);
                case HP_COMBINE -> renderCombineBeam(ctx, sw, sh, cx, cy, progress);
                case HP_PURPLE  -> renderPurpleRadiance(ctx, sw, sh, cx, cy, timer, progress);
                case HP_FIRE    -> renderFire(ctx, sw, sh, progress);
                default -> {}
            }
        });
    }

    // -----------------------------------------------------------------------
    // Phase renderers
    // -----------------------------------------------------------------------

    private static void renderCharge(DrawContext ctx, int sw, int sh,
                                     int cx, int cy, int timer, float progress) {
        renderBars(ctx, sw, sh, progress);

        double pulse = Math.sin(timer * 0.28) * 0.5 + 0.5;
        int bgA = (int)(pulse * progress * 65);
        fill(ctx, 0, 0, sw, sh, (bgA << 24) | 0x00080018);

        int leftX  = (int)(sw * 0.35f);
        int rightX = (int)(sw * 0.65f);

        // Blue energy crackles from left hand origin
        for (int i = 0; i < 7; i++) {
            double angle = i * Math.PI * 2.0 / 7 + timer * 0.075;
            double len   = Math.max(0, Math.sin(timer * 0.20 + i * 1.1)) * progress * sw * 0.18;
            int ex = leftX + (int)(Math.cos(angle) * len);
            int ey = cy    + (int)(Math.sin(angle) * len * 0.7);
            int la = (int)(Math.max(0, Math.sin(timer * 0.20 + i * 1.1)) * progress * 145);
            drawLine(ctx, leftX, cy, ex, ey, (la << 24) | 0x0033BBFF);
        }

        // Red energy crackles from right hand origin
        for (int i = 0; i < 7; i++) {
            double angle = i * Math.PI * 2.0 / 7 - timer * 0.075;
            double len   = Math.max(0, Math.sin(timer * 0.18 + i * 1.3)) * progress * sw * 0.18;
            int ex = rightX + (int)(Math.cos(angle) * len);
            int ey = cy     + (int)(Math.sin(angle) * len * 0.7);
            int la = (int)(Math.max(0, Math.sin(timer * 0.18 + i * 1.3)) * progress * 145);
            drawLine(ctx, rightX, cy, ex, ey, (la << 24) | 0x00EE4488);
        }
    }

    /** HP_COMBINE: purple beam stretches between the hand positions as they converge. */
    private static void renderCombineBeam(DrawContext ctx, int sw, int sh,
                                          int cx, int cy, float progress) {
        renderBars(ctx, sw, sh, 1f);

        float t     = easeInOut(progress);
        int   leftX = lerp((int)(sw * 0.35f), cx, t);
        int   rightX = lerp((int)(sw * 0.65f), cx, t);

        // Purple beam between hands
        if (rightX > leftX) {
            int beamA = (int)(progress * 180);
            fill(ctx, leftX, cy - 3, rightX, cy + 3, (beamA << 24) | 0x008833EE);
            int coreA = (int)(progress * 240);
            fill(ctx, leftX, cy - 1, rightX, cy + 1, (coreA << 24) | 0x00CCAAFF);
        }
    }

    /** HP_PURPLE: subtle purple radiance builds — world cube is the main visual. */
    private static void renderPurpleRadiance(DrawContext ctx, int sw, int sh,
                                             int cx, int cy, int timer, float progress) {
        renderBars(ctx, sw, sh, 1f);

        double pulse = Math.sin(timer * 0.38) * 0.06 + 1.0;
        int maxR = (int)(progress * 60 * pulse);
        for (int r = maxR; r > 0; r -= 6) {
            int a = (int)((float)r / Math.max(1, maxR) * progress * 110);
            fill(ctx, cx - r * 2, cy - r, cx + r * 2, cy + r,
                    (a << 24) | 0x006600BB);
        }
    }

    /** HP_FIRE: blinding white flash. */
    private static void renderFire(DrawContext ctx, int sw, int sh, float progress) {
        int purpleA = (int)((1f - progress) * 130);
        if (purpleA > 0) {
            fill(ctx, 0, 0, sw, sh, (purpleA << 24) | 0x006600BB);
        }
        int whiteA = (int)(progress * 255);
        fill(ctx, 0, 0, sw, sh, (whiteA << 24) | 0x00FFFFFF);
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

    private static void renderBars(DrawContext ctx, int sw, int sh, float alpha) {
        int barH = sh / 8;
        int a    = (int)(Math.min(1f, Math.max(0f, alpha)) * 255);
        fill(ctx, 0,       0,        sw, barH,      (a << 24));
        fill(ctx, 0, sh - barH, sw, sh,             (a << 24));
    }

    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static int lerp(int a, int b, float t) {
        return (int)(a + (b - a) * Math.max(0f, Math.min(1f, t)));
    }

    private static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        ctx.fill(x1, y1, x2, y2, color);
    }

    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0, dy = y1 - y0;
        int steps = Math.min(Math.max(Math.abs(dx), Math.abs(dy)), 2000);
        if (steps == 0) return;
        float xi = (float) dx / steps, yi = (float) dy / steps;
        float x = x0, y = y0;
        for (int i = 0; i <= steps; i++) {
            int px = (int) x, py = (int) y;
            if (px >= 0 && py >= 0 && px < 4000 && py < 4000) {
                ctx.fill(px, py, px + 1, py + 1, color);
            }
            x += xi;
            y += yi;
        }
    }
}
