package com.sloppyjoe.client.cutscene;

import com.sloppyjoe.sound.ModSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side state machine for domain expansion cutscenes.
 *
 * Phase flow (caster):
 *   CAST_CHARGE      (60t)  – camera orbits behind caster, dark energy crackling
 *   CUTSCENE_CLOSEUP (40t)  – camera swings to front and dollies in close to face
 *   CUTSCENE_HAND_UP (30t)  – arm rises toward blindfold (HUD overlay)
 *   CUTSCENE_LIFT    (25t)  – blindfold peels upward, blue eyes glow appear
 *   CUTSCENE_EYES    (20t)  – intense blue eye glow / lightning
 *   CUTSCENE_ZOOM_OUT(30t)  – camera pulls back to full body shot
 *   CUTSCENE_VOICE   (60t)  – arm raised, voice line plays, white radiance builds
 *   ACTIVATE_FLASH   (15t)  – blinding white flash
 *   VOID_INTRO       (50t)  – white void + perspective grid fades in
 *   ACTIVE           (600t) – stable domain; player can move and fight
 *   COLLAPSE         (25t)  – void shatters and fades out
 *
 * Captured players skip straight to ACTIVATE_FLASH when DomainActivatePayload arrives.
 */
@Environment(EnvType.CLIENT)
public class CutsceneManager {

    public enum Phase {
        NONE,
        CAST_CHARGE,        // 60t
        CUTSCENE_CLOSEUP,   // 40t
        CUTSCENE_HAND_UP,   // 30t
        CUTSCENE_LIFT,      // 25t
        CUTSCENE_EYES,      // 20t
        CUTSCENE_ZOOM_OUT,  // 30t
        CUTSCENE_VOICE,     // 60t
        ACTIVATE_FLASH,     // 15t
        VOID_INTRO,         // 50t
        ACTIVE,             // 600t
        COLLAPSE,           // 25t
        DONE
    }

    private static final int[] PHASE_DURATIONS = {
            0,   // NONE
            60,  // CAST_CHARGE
            40,  // CUTSCENE_CLOSEUP
            30,  // CUTSCENE_HAND_UP
            25,  // CUTSCENE_LIFT
            20,  // CUTSCENE_EYES
            30,  // CUTSCENE_ZOOM_OUT
            60,  // CUTSCENE_VOICE
            15,  // ACTIVATE_FLASH
            50,  // VOID_INTRO
            600, // ACTIVE
            25,  // COLLAPSE
            0    // DONE
    };

    private static boolean active = false;
    private static Phase phase = Phase.NONE;
    private static int phaseTimer = 0;
    private static String domainType = "";

    /** UUIDs in any domain cutscene (server mirrors this for damage immunity). */
    public static final Set<UUID> cutscenePlayers = new HashSet<>();

    // -----------------------------------------------------------------------
    // Cinematic camera state — TARGET values, smoothed per-frame in CameraMixin
    // -----------------------------------------------------------------------

    /** Orbit angle in radians: camera is at (sin(θ)*dist, heightOff, cos(θ)*dist) from player eye. */
    private static float cinematicOrbit = 0f;
    /** Distance from player in blocks. */
    private static float cinematicDist  = 3.5f;
    /** Height offset above player eye level (can be negative to drop below). */
    private static float cinematicHeightOff = 0f;
    /** True while the Camera mixin should override camera position. */
    private static boolean cinematicActive = false;
    /** Orbit angle at the start of the cinematic (camera behind player). */
    private static float orbitStart = 0f;

    /** Perspective saved before cutscene so we can restore it on end. */
    private static Perspective preCutscenePerspective = null;

    public static boolean isCinematicCameraActive() { return cinematicActive; }
    public static float getCinematicOrbit()          { return cinematicOrbit; }
    public static float getCinematicDist()           { return cinematicDist; }
    public static float getCinematicHeightOff()      { return cinematicHeightOff; }

    // -----------------------------------------------------------------------
    // Public API (called from network receivers in SloppyJoeModClient)
    // -----------------------------------------------------------------------

    /**
     * Called when DomainCastStartPayload arrives and the local player is the caster.
     * Starts the 3-second charge animation with a cinematic camera.
     */
    public static void startCastCharge(String type) {
        active    = true;
        domainType = type;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            // Save current perspective and force third-person so player body is visible
            preCutscenePerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);

            // orbitStart: camera positioned BEHIND the player.
            // Minecraft yaw: 0 = south (+Z), 90 = west (-X).
            // Player's forward direction: dx = -sin(yaw_rad), dz = cos(yaw_rad).
            // Our orbit formula: camX = px + sin(θ)*dist, camZ = pz + cos(θ)*dist.
            // Camera BEHIND player: sin(θ) = sin(yaw_rad), cos(θ) = -cos(yaw_rad)
            //   → θ = π - yaw_rad  (since sin(π-x)=sin(x), cos(π-x)=-cos(x)).
            float yawRad = (float) Math.toRadians(client.player.getYaw());
            orbitStart      = (float) Math.PI - yawRad;
            cinematicOrbit  = orbitStart;
        }

        cinematicDist      = 3.5f;
        cinematicHeightOff = 0.3f;  // slightly above eye level for an imposing shot
        cinematicActive    = true;

        setPhase(Phase.CAST_CHARGE);
        playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 0.6f);
    }

    /**
     * Called when DomainActivatePayload arrives and the local player is involved.
     * Caster: the phase machine is already running — just play the boom sound.
     * Captured player: start from ACTIVATE_FLASH.
     */
    public static void onDomainActivate(UUID casterUuid, String type) {
        domainType = type;
        active     = true;

        if (phase == Phase.NONE) {
            // Captured player — no camera cinematic, just flash + void
            cinematicActive = false;
            setPhase(Phase.ACTIVATE_FLASH);
        }
        // If already in CAST_CHARGE or a later phase the phase machine continues naturally.
        playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
    }

    /** Called when DomainExpirePayload arrives. */
    public static void onDomainExpire() {
        if (active && phase.ordinal() >= Phase.ACTIVE.ordinal()) {
            cinematicActive = false;
            setPhase(Phase.COLLAPSE);
            playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.7f);
        } else {
            endCutscene();
        }
    }

    public static void endCutscene() {
        active     = false;
        phase      = Phase.NONE;
        phaseTimer = 0;
        domainType = "";
        cinematicActive = false;

        // Restore the perspective the player had before the cutscene
        if (preCutscenePerspective != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.options.setPerspective(preCutscenePerspective);
            }
            preCutscenePerspective = null;
        }
    }

    // -----------------------------------------------------------------------
    // Tick (called from ClientTickEvents.END_CLIENT_TICK)
    // -----------------------------------------------------------------------

    public static void tick(MinecraftClient client) {
        if (!active) return;

        updateCinematicCamera();

        if (phase == Phase.CAST_CHARGE) {
            spawnCastChargeParticles(client);
        }

        phaseTimer++;
        int duration = getPhaseDuration(phase);
        if (duration > 0 && phaseTimer >= duration) {
            advancePhase();
        }
    }

    private static void spawnCastChargeParticles(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        float progress = getPhaseProgress();
        // Two SOUL_FIRE_FLAME arms spiraling around the player, tightening as progress grows
        double t      = phaseTimer * 0.28;
        double radius = 1.8 - progress * 0.6;   // shrinks from 1.8 → 1.2 as charge builds
        double ex = client.player.getX();
        double ey = client.player.getY() + 1.0;
        double ez = client.player.getZ();
        for (int i = 0; i < 2; i++) {
            double angle = t + i * Math.PI;
            double px = ex + Math.cos(angle) * radius;
            double pz = ez + Math.sin(angle) * radius;
            double py = ey + (Math.random() - 0.5) * 1.4;
            // Velocity spirals inward and upward
            double vx = (ex - px) * 0.06;
            double vy = 0.03 + Math.random() * 0.04;
            double vz = (ez - pz) * 0.06;
            client.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx, vy, vz);
        }
        // Sparse END_ROD sparkles, more frequent as charge nears completion
        if (phaseTimer % Math.max(1, (int)(3 * (1f - progress))) == 0) {
            double angle = t * 1.9;
            double r     = radius * 0.7;
            client.world.addParticle(ParticleTypes.END_ROD,
                    ex + Math.cos(angle) * r,
                    ey + (Math.random() - 0.3) * 1.2,
                    ez + Math.sin(angle) * r,
                    0, 0.06, 0);
        }
    }

    // -----------------------------------------------------------------------
    // Cinematic camera update (sets TARGET values for the Camera mixin)
    // -----------------------------------------------------------------------

    private static void updateCinematicCamera() {
        float p = getPhaseProgress();
        // Angle that puts the camera directly in front of the player
        float frontAngle = orbitStart + (float) Math.PI;

        switch (phase) {
            case CAST_CHARGE -> {
                // Slowly orbit behind the player (~72° arc over 60 ticks)
                cinematicOrbit     = orbitStart + phaseTimer * 0.020f;
                cinematicDist      = 3.5f;
                cinematicHeightOff = 0.3f;
                cinematicActive    = true;
            }
            case CUTSCENE_CLOSEUP -> {
                // Swing from behind to in front and dolly in to face level
                float orbitAfterCharge = orbitStart + 60 * 0.020f;
                cinematicOrbit     = lerpAngle(orbitAfterCharge, frontAngle, easeInOut(p));
                cinematicDist      = lerp(3.5f, 0.7f, easeInOut(p));
                cinematicHeightOff = lerp(0.3f, 0.0f, p);
                cinematicActive    = true;
            }
            case CUTSCENE_HAND_UP, CUTSCENE_LIFT, CUTSCENE_EYES -> {
                // Locked close-up, front view, eye level
                cinematicOrbit     = frontAngle;
                cinematicDist      = 0.7f;
                cinematicHeightOff = 0.0f;
                cinematicActive    = true;
            }
            case CUTSCENE_ZOOM_OUT -> {
                // Pull back to full-body wide shot
                cinematicOrbit     = frontAngle;
                cinematicDist      = lerp(0.7f, 4.0f, easeInOut(p));
                cinematicHeightOff = lerp(0.0f, -0.5f, p);   // drop slightly to frame full body
                cinematicActive    = true;
            }
            case CUTSCENE_VOICE -> {
                // Full-body wide shot with a very slight side angle for drama
                cinematicOrbit     = frontAngle + 0.08f;
                cinematicDist      = 4.0f;
                cinematicHeightOff = -0.5f;
                cinematicActive    = true;
            }
            default -> cinematicActive = false;
        }
    }

    // -----------------------------------------------------------------------
    // Phase advance + sounds
    // -----------------------------------------------------------------------

    private static void advancePhase() {
        int nextOrdinal = phase.ordinal() + 1;
        if (nextOrdinal >= Phase.values().length) {
            endCutscene();
            return;
        }
        Phase next = Phase.values()[nextOrdinal];
        setPhase(next);

        switch (next) {
            case CUTSCENE_VOICE -> playSound(ModSounds.DOMAIN_UNLIMITED_VOID_VOICE, 1.0f, 1.0f);
            case ACTIVE         -> {
                cinematicActive = false;
                playSound(SoundEvents.ENTITY_WARDEN_AMBIENT, 0.8f, 0.4f);
            }
            case COLLAPSE       -> playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.7f);
            case DONE           -> endCutscene();
            default             -> {}
        }
    }

    private static void setPhase(Phase p) {
        phase      = p;
        phaseTimer = 0;
    }

    private static void playSound(SoundEvent event, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        client.player.playSound(event, volume, pitch);
    }

    // -----------------------------------------------------------------------
    // Math helpers
    // -----------------------------------------------------------------------

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }

    /** Linear interpolation between two angles, taking the shortest arc. */
    private static float lerpAngle(float from, float to, float t) {
        float diff = to - from;
        while (diff >  Math.PI) diff -= 2f * (float) Math.PI;
        while (diff < -Math.PI) diff += 2f * (float) Math.PI;
        return from + diff * Math.max(0f, Math.min(1f, t));
    }

    /** Smoothstep — ease in-out cubic. */
    private static float easeInOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public static boolean isActive()       { return active; }
    public static Phase   getPhase()       { return phase; }
    public static int     getPhaseTimer()  { return phaseTimer; }
    public static String  getDomainType()  { return domainType; }

    /** True during the pre-domain cinematic phases: locks movement. */
    public static boolean isInCutscene() {
        if (!active) return false;
        return phase.ordinal() >= Phase.CAST_CHARGE.ordinal()
                && phase.ordinal() <= Phase.CUTSCENE_VOICE.ordinal();
    }

    public static boolean isInDomain() {
        return active && (phase == Phase.ACTIVE
                       || phase == Phase.VOID_INTRO
                       || phase == Phase.ACTIVATE_FLASH);
    }

    /** Progress through the current phase in [0, 1]. */
    public static float getPhaseProgress() {
        int dur = getPhaseDuration(phase);
        if (dur <= 0) return 1f;
        return Math.min(1f, (float) phaseTimer / dur);
    }

    private static int getPhaseDuration(Phase p) {
        if (p.ordinal() >= PHASE_DURATIONS.length) return 0;
        return PHASE_DURATIONS[p.ordinal()];
    }
}
