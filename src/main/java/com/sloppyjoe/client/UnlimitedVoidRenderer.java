package com.sloppyjoe.client;

import com.sloppyjoe.client.cutscene.CutsceneManager;
import com.sloppyjoe.client.cutscene.CutsceneManager.Phase;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Procedural HUD renderer for Gojo's Unlimited Void domain expansion.
 *
 * Phase visuals:
 *   CAST_CHARGE        – dark blue pulsing overlay + crackling energy lines
 *   CUTSCENE_CLOSEUP   – cinematic bars only (camera dolly handles drama)
 *   CUTSCENE_HAND_UP   – cinematic bars only (3D mixin handles head/arm pose)
 *   CUTSCENE_LIFT      – cinematic bars only (GojoBlindfoldRenderer handles 3D slide)
 *   CUTSCENE_EYES      – intense blue radial glow + lightning rays (screen-space bloom)
 *   CUTSCENE_ZOOM_OUT  – glow fades as camera pulls back
 *   CUTSCENE_VOICE     – four corner words slam in: DOMAIN / EXPANSION / UNLIMITED / VOID
 *   ACTIVATE_FLASH     – radial speed lines + blinding white flash
 *   VOID_INTRO         – white fades out, nebula builds in
 *   ACTIVE             – nebula background
 *   COLLAPSE           – nebula fades out
 */
@Environment(EnvType.CLIENT)
public class UnlimitedVoidRenderer {

    private static final Identifier DOMAIN_FONT = Identifier.of("sloppyjoe", "domain_title");

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> {
            if (!CutsceneManager.isActive()) return;
            if (!"unlimited_void".equals(CutsceneManager.getDomainType())) return;

            Phase phase    = CutsceneManager.getPhase();
            float progress = CutsceneManager.getPhaseProgress();
            int   timer    = CutsceneManager.getPhaseTimer();
            int   sw       = ctx.getScaledWindowWidth();
            int   sh       = ctx.getScaledWindowHeight();
            int   cx       = sw / 2;
            int   cy       = sh / 2;

            switch (phase) {
                case CAST_CHARGE      -> renderCastCharge(ctx, sw, sh, cx, cy, timer, progress);
                case CUTSCENE_CLOSEUP -> renderCinematicBars(ctx, sw, sh, 1f);
                case CUTSCENE_HAND_UP -> renderCinematicBars(ctx, sw, sh, 1f);
                case CUTSCENE_LIFT    -> renderCinematicBars(ctx, sw, sh, 1f);
                case CUTSCENE_EYES    -> renderEyeGlow(ctx, sw, sh, cx, cy, timer, progress);
                case CUTSCENE_ZOOM_OUT -> {
                    renderCinematicBars(ctx, sw, sh, 1f);
                    renderEyeGlow(ctx, sw, sh, cx, cy, timer, 1f - progress);
                }
                case CUTSCENE_VOICE   -> renderVoice(ctx, sw, sh, cx, cy, timer, progress);
                case ACTIVATE_FLASH   -> renderActivateFlash(ctx, sw, sh, cx, cy, timer, progress);
                case VOID_INTRO       -> renderVoidIntro(ctx, sw, sh, cx, cy, timer, progress);
                case ACTIVE           -> renderNebula(ctx, sw, sh, cx, cy, timer, 1f);
                case COLLAPSE         -> renderNebula(ctx, sw, sh, cx, cy, timer, 1f - progress);
                default -> {}
            }
        });
    }

    // -----------------------------------------------------------------------
    // Cinematic helpers
    // -----------------------------------------------------------------------

    private static void renderCinematicBars(DrawContext ctx, int sw, int sh, float alpha) {
        int barH = sh / 8;
        int a = (int)(Math.min(1f, alpha) * 255);
        fill(ctx, 0, 0, sw, barH, (a << 24));
        fill(ctx, 0, sh - barH, sw, sh, (a << 24));
    }

    // -----------------------------------------------------------------------
    // Phase renderers
    // -----------------------------------------------------------------------

    private static void renderCastCharge(DrawContext ctx, int sw, int sh,
                                         int cx, int cy, int timer, float progress) {
        renderCinematicBars(ctx, sw, sh, progress);

        double pulse = Math.sin(timer * 0.22) * 0.5 + 0.5;
        int bgAlpha = (int)(pulse * progress * 90);
        fill(ctx, 0, 0, sw, sh, (bgAlpha << 24) | 0x00000A1A);

        int numLines = 10;
        for (int i = 0; i < numLines; i++) {
            double angle    = i * Math.PI * 2.0 / numLines + timer * 0.055;
            double lenFactor = Math.max(0, Math.sin(timer * 0.20 + i * 1.3));
            float  len      = (float)(lenFactor * progress * Math.min(sw, sh) * 0.30f);
            int ex = cx + (int)(Math.cos(angle) * len);
            int ey = cy + (int)(Math.sin(angle) * len);
            int lineAlpha = (int)(lenFactor * progress * 170);
            drawLine(ctx, cx, cy, ex, ey, (lineAlpha << 24) | 0x0044AAFF);
        }
        for (int i = 0; i < numLines; i++) {
            double angle     = (i + 0.5) * Math.PI * 2.0 / numLines - timer * 0.04;
            double lenFactor = Math.max(0, Math.sin(timer * 0.15 + i * 0.9 + 1.0));
            float  len       = (float)(lenFactor * progress * Math.min(sw, sh) * 0.18f);
            int ex = cx + (int)(Math.cos(angle) * len);
            int ey = cy + (int)(Math.sin(angle) * len);
            int lineAlpha = (int)(lenFactor * progress * 120);
            drawLine(ctx, cx, cy, ex, ey, (lineAlpha << 24) | 0x0088CCFF);
        }
    }

    private static void renderEyeGlow(DrawContext ctx, int sw, int sh,
                                      int cx, int cy, int timer, float progress) {
        renderCinematicBars(ctx, sw, sh, 1f);

        int eyeY = cy;
        double pulse = Math.sin(timer * 0.55) * 0.2 + 0.8;

        for (int r = 75; r > 0; r -= 4) {
            int a = (int)(progress * 230 * r / 75 * pulse);
            fill(ctx, cx - r * 2, eyeY - r, cx + r * 2, eyeY + r,
                    (a << 24) | 0x001166FF);
        }

        int hc = (int)(progress * 255);
        fill(ctx, cx - 16, eyeY - 6, cx + 16, eyeY + 6,
                (hc << 24) | 0x00AADDFF);

        int numRays = 8;
        for (int i = 0; i < numRays; i++) {
            double angle  = i * Math.PI * 2.0 / numRays + timer * 0.08;
            float  rayLen = (float)(Math.abs(Math.sin(timer * 0.28 + i * 1.9)) * 0.5 + 0.5)
                            * (sh / 8f);
            int ex = cx + (int)(Math.cos(angle) * rayLen);
            int ey = eyeY + (int)(Math.sin(angle) * rayLen * 0.45f);
            int ra = (int)(progress * 175);
            drawLine(ctx, cx, eyeY, ex, ey, (ra << 24) | 0x0055AAFF);
        }
    }

    /**
     * CUTSCENE_VOICE: four words slam into the screen corners in sequence.
     * Synced to the voice line "Domain — Expansion — Unlimited — Void".
     *
     * TL → DOMAIN   (slams in at progress ~0.08)
     * TR → EXPANSION (slams in at progress ~0.28)
     * BL → UNLIMITED (slams in at progress ~0.52)
     * BR → VOID      (slams in at progress ~0.72)
     */
    private static void renderVoice(DrawContext ctx, int sw, int sh,
                                    int cx, int cy, int timer, float progress) {
        renderCinematicBars(ctx, sw, sh, 1f);

        // White radiance builds after all words have landed
        if (progress > 0.65f) {
            float rp = (progress - 0.65f) / 0.35f;
            for (int r = 100; r > 0; r -= 8) {
                int a = (int)(rp * 170 * r / 100);
                fill(ctx, cx - r, cy - r / 2, cx + r, cy + r / 2, (a << 24) | 0x00FFFFFF);
            }
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        int barH   = sh / 8;
        int margin = sw / 22;

        // Slam timings — tuned to voice line cadence
        float slam1 = clamp01((progress - 0.00f) / 0.12f);  // DOMAIN    → TL  (tick  0)
        float slam2 = clamp01((progress - 0.14f) / 0.12f);  // EXPANSION → TR  (tick  8)
        float slam3 = clamp01((progress - 0.32f) / 0.12f);  // UNLIMITED → BL  (tick 19)
        float slam4 = clamp01((progress - 0.48f) / 0.12f);  // VOID      → BR  (tick 29)

        Text word1 = Text.literal("DOMAIN")   .setStyle(Style.EMPTY.withFont(DOMAIN_FONT));
        Text word2 = Text.literal("EXPANSION").setStyle(Style.EMPTY.withFont(DOMAIN_FONT));
        Text word3 = Text.literal("UNLIMITED").setStyle(Style.EMPTY.withFont(DOMAIN_FONT));
        Text word4 = Text.literal("VOID")     .setStyle(Style.EMPTY.withFont(DOMAIN_FONT));

        float baseScale = 2.2f;
        int textH = (int)(client.textRenderer.fontHeight * baseScale);

        int topY    = barH + margin + textH / 2;
        int bottomY = sh - barH - margin - textH / 2;

        if (slam1 > 0f) drawCornerSlamText(ctx, client, word1, margin,        topY,    slam1, false, baseScale, 0xFF66AAFF);
        if (slam2 > 0f) drawCornerSlamText(ctx, client, word2, sw - margin,   topY,    slam2, true,  baseScale, 0xFF66AAFF);
        if (slam3 > 0f) drawCornerSlamText(ctx, client, word3, margin,        bottomY, slam3, false, baseScale, 0xFFAADDFF);
        if (slam4 > 0f) drawCornerSlamText(ctx, client, word4, sw - margin,   bottomY, slam4, true,  baseScale, 0xFFFFFFFF);
    }

    /**
     * Renders a word slamming in from the edge toward its final position.
     * anchorX: left edge of text (rightAligned=false) or right edge (rightAligned=true).
     * anchorY: vertical center of the final text position.
     */
    private static void drawCornerSlamText(DrawContext ctx, MinecraftClient client,
                                           Text text, int anchorX, int anchorY,
                                           float slamT, boolean rightAligned,
                                           float finalScale, int solidColor) {
        float eased = 1f - (1f - slamT) * (1f - slamT) * (1f - slamT);
        float scale = finalScale * (2.8f - 1.8f * eased);

        int alpha = Math.min(255, (int)(slamT * 1800));
        if (alpha <= 0) return;

        // Impact glow flares at the moment of impact
        float glowT = Math.max(0f, 1f - Math.abs(slamT - 0.42f) / 0.22f);
        if (glowT > 0f) {
            int glowA = (int)(glowT * glowT * 190);
            int hw = (int)(client.textRenderer.getWidth(text) * scale * 0.55f + 8);
            int hh = (int)(client.textRenderer.fontHeight * scale * 0.55f + 4);
            fill(ctx, anchorX - (rightAligned ? hw * 2 : 0) - 2, anchorY - hh,
                    anchorX + (rightAligned ? 2 : hw * 2) + 2, anchorY + hh,
                    (glowA << 24) | 0x00FFFFFF);
        }

        int colorFinal = (alpha << 24) | (solidColor & 0x00FFFFFF);
        MatrixStack matrices = ctx.getMatrices();
        matrices.push();

        if (rightAligned) {
            int scaledW = (int)(client.textRenderer.getWidth(text) * scale);
            matrices.translate((float)(anchorX - scaledW), (float)(anchorY - (int)(client.textRenderer.fontHeight * scale / 2f)), 0f);
        } else {
            matrices.translate((float)anchorX, (float)(anchorY - (int)(client.textRenderer.fontHeight * scale / 2f)), 0f);
        }
        matrices.scale(scale, scale, 1f);
        ctx.drawTextWithShadow(client.textRenderer, text, 0, 0, colorFinal);
        matrices.pop();
    }

    /**
     * ACTIVATE_FLASH: radial speed lines burst from centre, then white flash builds.
     * Gives an anime-style "domain expanding" warp effect.
     */
    private static void renderActivateFlash(DrawContext ctx, int sw, int sh,
                                            int cx, int cy, int timer, float progress) {
        // Speed lines — many thin white lines radiating from center
        int numLines = 56;
        for (int i = 0; i < numLines; i++) {
            double angle = i * Math.PI * 2.0 / numLines;
            // Vary length per line for organic feel
            float lenFactor = 0.55f + (i % 6) * 0.08f;
            float lineLen   = Math.min(sw, sh) * lenFactor;
            float innerR    = progress * 22f;  // inner gap grows slightly

            int sx = cx + (int)(Math.cos(angle) * innerR);
            int sy = cy + (int)(Math.sin(angle) * innerR);
            int ex = cx + (int)(Math.cos(angle) * lineLen);
            int ey = cy + (int)(Math.sin(angle) * lineLen);

            int lineAlpha = (int)(Math.min(1f, progress * 2.5f) * 210);
            drawLine(ctx, sx, sy, ex, ey, (lineAlpha << 24) | 0x00FFFFFF);
        }

        // Bright centre pulse
        for (int r = 40; r > 0; r -= 5) {
            int a = (int)(progress * 200 * r / 40);
            fill(ctx, cx - r, cy - r, cx + r, cy + r, (a << 24) | 0x00FFFFFF);
        }

        // Full white flash builds from 45% onward
        if (progress > 0.45f) {
            float flashT = (progress - 0.45f) / 0.55f;
            int flashA = (int)(flashT * flashT * 255);
            fill(ctx, 0, 0, sw, sh, (flashA << 24) | 0x00FFFFFF);
        }
    }

    /**
     * VOID_INTRO: white flash fades out while the nebula background fades in.
     */
    private static void renderVoidIntro(DrawContext ctx, int sw, int sh,
                                        int cx, int cy, int timer, float progress) {
        // White fades out in first 25%
        if (progress < 0.25f) {
            int whiteA = (int)((1f - progress / 0.25f) * 255);
            fill(ctx, 0, 0, sw, sh, (whiteA << 24) | 0x00FFFFFF);
        }
        // Nebula fades in
        float nebulaIntensity = clamp01(progress * 2.5f);
        renderNebula(ctx, sw, sh, cx, cy, timer, nebulaIntensity);
    }

    /**
     * Renders a procedural space nebula as a HUD overlay.
     * Uses layered soft ellipse shapes in purple/blue/teal — low enough opacity
     * that gameplay below remains legible, but the void sky looks cosmic.
     */
    private static void renderNebula(DrawContext ctx, int sw, int sh,
                                     int cx, int cy, int timer, float intensity) {
        if (intensity <= 0f) return;

        // Overall deep-space tint (extremely subtle on gameplay area)
        int tintA = (int)(intensity * 18);
        fill(ctx, 0, 0, sw, sh, (tintA << 24) | 0x000A0520);

        // Upper-half sky region slightly stronger
        int skyTintA = (int)(intensity * 20);
        fill(ctx, 0, 0, sw, sh / 2, (skyTintA << 24) | 0x00150030);

        // Cloud 1: large purple nebula — upper-right quadrant
        drawNebulaCloud(ctx, (int)(sw * 0.74f), (int)(sh * 0.17f),
                (int)(sw * 0.36f), (int)(sh * 0.22f), 0x00551199, intensity, 0.09f);

        // Cloud 2: blue/teal — upper-left
        drawNebulaCloud(ctx, (int)(sw * 0.26f), (int)(sh * 0.21f),
                (int)(sw * 0.32f), (int)(sh * 0.18f), 0x00113366, intensity, 0.08f);

        // Cloud 3: teal accent — top-center
        drawNebulaCloud(ctx, cx, (int)(sh * 0.09f),
                (int)(sw * 0.22f), (int)(sh * 0.11f), 0x00006655, intensity, 0.07f);

        // Cloud 4: bright magenta highlight — small, vivid
        drawNebulaCloud(ctx, (int)(sw * 0.63f), (int)(sh * 0.07f),
                (int)(sw * 0.13f), (int)(sh * 0.08f), 0x00AA2277, intensity * 1.3f, 0.11f);

        // Cloud 5: deep violet spread — top-center
        drawNebulaCloud(ctx, cx, (int)(sh * 0.25f),
                (int)(sw * 0.28f), (int)(sh * 0.15f), 0x00330055, intensity, 0.06f);

        // Slow-drifting shimmer: subtle bright streaks along the upper edge
        int shimmerT = timer / 3;
        for (int i = 0; i < 3; i++) {
            int sx = (int)(sw * (0.2f + i * 0.3f + (shimmerT % 40) * 0.005f));
            int shimA = (int)(intensity * 22);
            fill(ctx, sx - sw / 12, 0, sx + sw / 12, sh / 18, (shimA << 24) | 0x00AABBDD);
        }
    }

    /**
     * Draws a soft, diffuse cloud using nested progressively-fading rectangles.
     * cx/cy — cloud centre; rx/ry — half-extents; color — 0x00RRGGBB;
     * intensity — [0,1] multiplier; maxAlphaFrac — max alpha as fraction of 255 at centre.
     */
    private static void drawNebulaCloud(DrawContext ctx, int cx, int cy,
                                        int rx, int ry, int color,
                                        float intensity, float maxAlphaFrac) {
        int maxA = (int)(maxAlphaFrac * 255 * Math.min(1f, intensity));
        if (maxA <= 0 || rx <= 0 || ry <= 0) return;
        int steps = Math.max(3, Math.min(rx, ry) / 7);
        for (int i = steps; i > 0; i--) {
            float t    = (float) i / steps;            // t=1 at edge, near 0 at centre
            float fade = (1f - t) * (1f - t);          // quadratic brightening toward centre
            int   a    = (int)(fade * maxA);
            if (a <= 0) continue;
            int crx = (int)(rx * t);
            int cry = (int)(ry * t);
            fill(ctx, cx - crx, cy - cry, cx + crx, cy + cry, (a << 24) | (color & 0x00FFFFFF));
        }
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    // -----------------------------------------------------------------------
    // Drawing primitives
    // -----------------------------------------------------------------------

    private static void fill(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        ctx.fill(x1, y1, x2, y2, color);
    }

    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx    = x1 - x0;
        int dy    = y1 - y0;
        int steps = Math.min(Math.max(Math.abs(dx), Math.abs(dy)), 2000);
        if (steps == 0) return;
        float xi  = (float) dx / steps;
        float yi  = (float) dy / steps;
        float x   = x0, y = y0;
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
