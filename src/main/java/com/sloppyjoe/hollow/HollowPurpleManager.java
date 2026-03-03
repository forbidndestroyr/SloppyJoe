package com.sloppyjoe.hollow;

import com.sloppyjoe.domain.DomainManager;
import com.sloppyjoe.entity.HollowPurpleEntity;
import com.sloppyjoe.entity.ModEntities;
import com.sloppyjoe.item.ModItems;
import com.sloppyjoe.network.HollowPurpleStartPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Manages the Hollow Purple special attack:
 *
 *  Cooldown
 *  --------
 *  - Inside active domain as caster: no cooldown.
 *  - Outside domain: 5-minute (COOLDOWN_TICKS) cooldown between casts.
 *
 *  XP management (while Gojo's Blindfold is equipped)
 *  ---------------------------------------------------
 *  - On equip  : save (level, progress, total) → zero player XP → block addExperience.
 *  - On unequip: restore saved XP → unblock addExperience.
 *  - While on cooldown: override XP bar every tick to show countdown
 *      level = remaining seconds, bar = remaining / COOLDOWN_TICKS (fills right → left).
 */
public class HollowPurpleManager {

    /** 5 minutes = 300 seconds = 6 000 ticks. */
    public static final int COOLDOWN_TICKS = 6000;

    /** UUID → remaining cooldown ticks. */
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    /** UUID → ticks until the Hollow Purple projectile fires (after cutscene reaches HP_FIRE). */
    private static final Map<UUID, Integer> scheduledFires = new HashMap<>();

    /** UUIDs currently wearing Gojo's Blindfold (server-side tracking). */
    private static final Set<UUID> wearing = new HashSet<>();

    /**
     * UUIDs whose addExperience should be silently cancelled.
     * Read by ServerPlayerXpMixin.
     */
    private static final Set<UUID> xpBlocked = new HashSet<>();

    /** Saved XP state keyed by UUID. */
    private static final Map<UUID, SavedXp> savedXp = new HashMap<>();

    private record SavedXp(int level, float progress, int total) {}

    // -----------------------------------------------------------------------
    // Server tick  (registered in SloppyJoeMod)
    // -----------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        // 1. Decrement cooldowns; restore XP bar when a cooldown expires
        Iterator<Map.Entry<UUID, Integer>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
                // Cooldown just expired — restore normal XP display if player is online
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player != null) restoreXpDisplay(player);
            } else {
                e.setValue(remaining);
            }
        }

        // 2. Fire scheduled Hollow Purple projectiles (fires at tick 100 = start of HP_FIRE phase)
        Iterator<Map.Entry<UUID, Integer>> fireIt = scheduledFires.entrySet().iterator();
        while (fireIt.hasNext()) {
            Map.Entry<UUID, Integer> e = fireIt.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                fireIt.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(e.getKey());
                if (player != null) fireProjectile(player);
            } else {
                e.setValue(remaining);
            }
        }

        // 3. Equip / unequip detection + cooldown bar update (all online players)
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            boolean isWearing = player.getEquippedStack(EquipmentSlot.HEAD)
                    .isOf(ModItems.GOJO_BLINDFOLD);
            boolean wasWearing = wearing.contains(uuid);

            if (isWearing && !wasWearing) {
                onEquip(player);
                wearing.add(uuid);
            } else if (!isWearing && wasWearing) {
                onUnequip(player);
                wearing.remove(uuid);
            }

            // Override XP bar every tick while on cooldown
            if (isWearing && isOnCooldown(uuid)) {
                int rem = getRemainingTicks(uuid);
                // level = remaining seconds; bar drains from 1.0 → 0
                player.experienceLevel    = rem / 20;
                player.experienceProgress = (float) rem / COOLDOWN_TICKS;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Attempt to cast Hollow Purple.
     * Sends S2C cutscene trigger; projectile fires server-side after 100 ticks
     * (aligned with the HP_FIRE phase of the client cutscene).
     * No-ops silently if on cooldown or a cast is already in flight.
     */
    public static void tryCast(ServerPlayerEntity caster) {
        UUID uuid = caster.getUuid();
        boolean inDomain = DomainManager.isActiveCaster(uuid);
        if (!inDomain && isOnCooldown(uuid)) return;
        if (scheduledFires.containsKey(uuid)) return;  // already casting

        // Trigger the client-side cutscene
        ServerPlayNetworking.send(caster, new HollowPurpleStartPayload());
        // Schedule projectile to fire at tick 100 (start of HP_FIRE cutscene phase)
        scheduledFires.put(uuid, 100);

        if (!inDomain) {
            cooldowns.put(uuid, COOLDOWN_TICKS);
        }
    }

    public static boolean isOnCooldown(UUID uuid) {
        return cooldowns.getOrDefault(uuid, 0) > 0;
    }

    public static int getRemainingTicks(UUID uuid) {
        return cooldowns.getOrDefault(uuid, 0);
    }

    /** Called by ServerPlayerXpMixin — returns true while blindfold is worn. */
    public static boolean isXpBlocked(UUID uuid) {
        return xpBlocked.contains(uuid);
    }

    // -----------------------------------------------------------------------
    // Equip / unequip logic
    // -----------------------------------------------------------------------

    private static void onEquip(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        // Save current XP state
        savedXp.put(uuid, new SavedXp(
                player.experienceLevel,
                player.experienceProgress,
                player.totalExperience));

        // Zero all XP
        player.experienceLevel    = 0;
        player.experienceProgress = 0f;
        player.totalExperience    = 0;

        // Block further XP gain
        xpBlocked.add(uuid);
    }

    private static void onUnequip(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        xpBlocked.remove(uuid);
        restoreXpDisplay(player);
    }

    /**
     * Writes saved XP back onto the player and clears the saved record.
     * Safe to call even if no XP was saved (no-ops cleanly).
     */
    private static void restoreXpDisplay(ServerPlayerEntity player) {
        SavedXp saved = savedXp.remove(player.getUuid());
        if (saved == null) return;

        player.experienceLevel    = saved.level();
        player.experienceProgress = saved.progress();
        player.totalExperience    = saved.total();
    }

    // -----------------------------------------------------------------------
    // Projectile spawn
    // -----------------------------------------------------------------------

    private static void fireProjectile(ServerPlayerEntity caster) {
        ServerWorld world = (ServerWorld) caster.getWorld();

        HollowPurpleEntity proj = ModEntities.HOLLOW_PURPLE.create(world, SpawnReason.COMMAND);
        if (proj == null) return;

        Vec3d look   = caster.getRotationVector();
        Vec3d origin = caster.getEyePos().add(look.multiply(1.2));
        proj.setPosition(origin);
        proj.setCasterUuid(caster.getUuid());
        proj.setVelocity(look.multiply(4.0));

        world.spawnEntity(proj);
    }
}
