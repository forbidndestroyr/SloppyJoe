package com.sloppyjoe.entity;

import com.sloppyjoe.ModGameRules;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gojo's Hollow Purple attack — a large, fast-moving purple sphere that
 * passes through terrain (optionally destroying it) and instantly kills
 * any LivingEntity it contacts.
 *
 * Movement: 4 blocks/tick, no gravity, noClip.
 * Block destruction: 3×3×3 cube around the projectile centre each tick,
 *   skips blocks with hardness == -1 (bedrock, end portal frame, etc.).
 *   Gated by the "hollowPurpleGriefing" gamerule (default: on).
 * Despawn: after MAX_TICKS (200 ticks / 10 seconds).
 */
public class HollowPurpleEntity extends Entity {

    private static final int MAX_TICKS = 200;

    private UUID casterUuid = null;
    private int ticksLived   = 0;

    public HollowPurpleEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    // -----------------------------------------------------------------------
    // Caster UUID (stored in NBT for persistence across chunk reloads)
    // -----------------------------------------------------------------------

    public void setCasterUuid(UUID uuid) {
        this.casterUuid = uuid;
    }

    public UUID getCasterUuid() {
        return casterUuid;
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        Vec3d vel = this.getVelocity();
        this.setPosition(this.getX() + vel.x, this.getY() + vel.y, this.getZ() + vel.z);

        if (this.getWorld().isClient()) return;

        ServerWorld sworld = (ServerWorld) this.getWorld();
        ticksLived++;

        // Particle trail — DRAGON_BREATH (purple/violet) + END_ROD sparkles
        sworld.spawnParticles(ParticleTypes.DRAGON_BREATH,
                this.getX(), this.getY(), this.getZ(),
                12, 0.25, 0.25, 0.25, 0.03);
        sworld.spawnParticles(ParticleTypes.END_ROD,
                this.getX(), this.getY(), this.getZ(),
                4, 0.15, 0.15, 0.15, 0.0);

        // Block destruction — 3×3×3 cube around projectile centre
        if (sworld.getGameRules().getBoolean(ModGameRules.HOLLOW_PURPLE_GRIEFING)) {
            BlockPos centre = BlockPos.ofFloored(this.getPos());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos bp = centre.add(dx, dy, dz);
                        BlockState state = sworld.getBlockState(bp);
                        // Skip air, and unbreakable blocks (hardness == -1)
                        if (!state.isAir() && state.getHardness(sworld, bp) >= 0) {
                            sworld.breakBlock(bp, false);
                        }
                    }
                }
            }
        }

        // Entity hit detection — 1.5-block sphere around projectile
        Box hitBox = new Box(
                this.getX() - 0.75, this.getY() - 0.75, this.getZ() - 0.75,
                this.getX() + 0.75, this.getY() + 0.75, this.getZ() + 0.75);

        List<Entity> targets = sworld.getOtherEntities(this, hitBox, e ->
                e instanceof LivingEntity && !e.getUuid().equals(casterUuid));

        for (Entity target : targets) {
            if (target instanceof ServerPlayerEntity player) {
                player.kill(sworld);
            } else if (target instanceof LivingEntity living) {
                living.kill(sworld);
            }
        }

        // Despawn after MAX_TICKS
        if (ticksLived >= MAX_TICKS) {
            this.discard();
        }
    }

    // -----------------------------------------------------------------------
    // Entity boilerplate
    // -----------------------------------------------------------------------

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        ticksLived = nbt.getInt("TicksLived");
        if (nbt.containsUuid("CasterUuid")) {
            casterUuid = nbt.getUuid("CasterUuid");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("TicksLived", ticksLived);
        if (casterUuid != null) nbt.putUuid("CasterUuid", casterUuid);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false; // immune to all damage
    }

    @Override
    public boolean isCollidable() { return false; }

    @Override
    public boolean canHit() { return false; }
}
