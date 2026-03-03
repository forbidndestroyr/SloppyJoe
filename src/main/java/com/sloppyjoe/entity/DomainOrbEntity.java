package com.sloppyjoe.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Floating black orb that marks an active domain expansion.
 * Stores the caster UUID, domain type, and the set of players captured inside.
 */
public class DomainOrbEntity extends Entity {

    private static final TrackedData<Optional<UUID>> CASTER_UUID =
            DataTracker.registerData(DomainOrbEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private static final TrackedData<String> DOMAIN_TYPE =
            DataTracker.registerData(DomainOrbEntity.class, TrackedDataHandlerRegistry.STRING);

    // Server-side set; not synced to clients (clients use DomainActivatePayload)
    private final Set<UUID> capturedInside = new HashSet<>();

    public DomainOrbEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CASTER_UUID, Optional.empty());
        builder.add(DOMAIN_TYPE, "");
    }

    public UUID getCasterUuid() {
        return this.dataTracker.get(CASTER_UUID).orElse(null);
    }

    public void setCasterUuid(UUID uuid) {
        this.dataTracker.set(CASTER_UUID, Optional.of(uuid));
    }

    public String getDomainType() {
        return this.dataTracker.get(DOMAIN_TYPE);
    }

    public void setDomainType(String type) {
        this.dataTracker.set(DOMAIN_TYPE, type);
    }

    public Set<UUID> getCapturedInside() {
        return Collections.unmodifiableSet(capturedInside);
    }

    public void addCaptured(UUID uuid) {
        capturedInside.add(uuid);
    }

    public void removeCaptured(UUID uuid) {
        capturedInside.remove(uuid);
    }

    @Override
    public void tick() {
        super.tick();
        // Reposition captured players who drift too far — but only if they are
        // in the same world as the orb (players teleported to the void dimension
        // would otherwise be yanked back to the orb's overworld position).
        if (!this.getWorld().isClient()) {
            for (UUID uuid : new HashSet<>(capturedInside)) {
                var player = this.getWorld().getServer().getPlayerManager().getPlayer(uuid);
                if (player != null
                        && player.getWorld() == this.getWorld()
                        && player.squaredDistanceTo(this) > 400.0) {
                    player.setPosition(this.getX(), this.getY(), this.getZ());
                }
            }
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("CasterUuid")) {
            setCasterUuid(nbt.getUuid("CasterUuid"));
        }
        setDomainType(nbt.getString("DomainType"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        UUID caster = getCasterUuid();
        if (caster != null) {
            nbt.putUuid("CasterUuid", caster);
        }
        nbt.putString("DomainType", getDomainType());
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }
}
