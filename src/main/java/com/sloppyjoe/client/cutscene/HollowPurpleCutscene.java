package com.sloppyjoe.client.cutscene;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Client-side state machine for Gojo's Hollow Purple special attack cutscene.
 *
 * Phase flow:
 *   HP_CHARGE  (25t) – dark energy crackles from both sides of screen
 *   HP_BLUE    (20t) – blue infinity orb forms on the left
 *   HP_RED     (20t) – red infinity orb forms on the right
 *   HP_COMBINE (20t) – both orbs converge to centre and merge purple
 *   HP_PURPLE  (15t) – massive hollow purple sphere materialises
 *   HP_FIRE    (10t) – white flash; projectile fires server-side at tick ~100
 *
 * Total: 110 ticks (5.5 seconds).
 * Server fires projectile at tick 100 (start of HP_FIRE).
 */
@Environment(EnvType.CLIENT)
public class HollowPurpleCutscene {

    public enum Phase {
        NONE,
        HP_CHARGE,   // 25t
        HP_BLUE,     // 20t
        HP_RED,      // 20t
        HP_COMBINE,  // 20t
        HP_PURPLE,   // 15t
        HP_FIRE,     // 10t
        DONE
    }

    private static final int[] DURATIONS = {
            0,   // NONE
            25,  // HP_CHARGE
            20,  // HP_BLUE
            20,  // HP_RED
            20,  // HP_COMBINE
            15,  // HP_PURPLE
            10,  // HP_FIRE
            0    // DONE
    };

    private static boolean active     = false;
    private static Phase   phase      = Phase.NONE;
    private static int     phaseTimer = 0;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Called from the HollowPurpleStartPayload S2C receiver. */
    public static void start() {
        if (active) return;
        active = true;
        setPhase(Phase.HP_CHARGE);
        playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
    }

    public static void end() {
        active     = false;
        phase      = Phase.NONE;
        phaseTimer = 0;
    }

    // -----------------------------------------------------------------------
    // Tick (called from ClientTickEvents.END_CLIENT_TICK in SloppyJoeModClient)
    // -----------------------------------------------------------------------

    public static void tick(MinecraftClient client) {
        if (!active) return;
        phaseTimer++;
        int dur = getDuration(phase);
        if (dur > 0 && phaseTimer >= dur) {
            advance();
        }
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private static void advance() {
        int nextOrd = phase.ordinal() + 1;
        if (nextOrd >= Phase.values().length) {
            end();
            return;
        }
        Phase next = Phase.values()[nextOrd];
        setPhase(next);
        switch (next) {
            case HP_COMBINE -> playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,  1.0f, 1.4f);
            case HP_FIRE    -> playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM,     1.5f, 0.45f);
            case DONE       -> end();
            default         -> {}
        }
    }

    private static void setPhase(Phase p) {
        phase      = p;
        phaseTimer = 0;
    }

    private static void playSound(SoundEvent event, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.playSound(event, volume, pitch);
        }
    }

    private static int getDuration(Phase p) {
        if (p.ordinal() >= DURATIONS.length) return 0;
        return DURATIONS[p.ordinal()];
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public static boolean isActive()        { return active; }
    public static Phase   getPhase()        { return phase; }
    public static int     getPhaseTimer()   { return phaseTimer; }

    public static float getPhaseProgress() {
        int dur = getDuration(phase);
        if (dur <= 0) return 1f;
        return Math.min(1f, (float) phaseTimer / dur);
    }
}
