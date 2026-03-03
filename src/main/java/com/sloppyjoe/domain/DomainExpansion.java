package com.sloppyjoe.domain;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;

import java.util.Set;
import java.util.UUID;

/**
 * Interface for a Domain Expansion. Each domain implements its own
 * identifier, required helmet item, per-tick effects, and lifecycle hooks.
 */
public interface DomainExpansion {

    /** Unique string ID, e.g. "unlimited_void". Used in network payloads and cutscene routing. */
    String getId();

    /** Item that must be in the player's helmet slot to cast this domain. */
    Item getRequiredHelmetItem();

    /** Duration in ticks the domain stays active after the cutscene ends. */
    default int getDurationTicks() { return 600; }

    /** Radius in blocks around the caster at activation to auto-capture nearby players. */
    default double getCaptureRadius() { return 10.0; }

    /**
     * Called every server tick while the domain is active.
     * Apply per-tick effects to the caster and captured players here.
     */
    void tick(ServerWorld world, PlayerEntity caster, Set<UUID> captured);

    /** Called once when the domain successfully activates (after 3s cast). */
    void onActivate(ServerWorld world, PlayerEntity caster);

    /** Called once when the domain expires (timeout or caster death). */
    void onExpire(ServerWorld world, PlayerEntity caster);
}
