package com.sloppyjoe.domain;

import com.sloppyjoe.item.ModItems;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Set;
import java.util.UUID;

/**
 * Gojo Satoru's Unlimited Void domain expansion.
 *
 * Caster:  Strength II + Speed II for domain duration.
 * Targets: Slowness 255 + frozenTicks=300 + Nausea I per tick.
 * All captured: Glowing for domain duration.
 * On expire: caster receives Weakness I + Mining Fatigue I for 3s.
 */
public class UnlimitedVoidDomain implements DomainExpansion {

    public static final UnlimitedVoidDomain INSTANCE = new UnlimitedVoidDomain();

    private UnlimitedVoidDomain() {}

    @Override
    public String getId() {
        return "unlimited_void";
    }

    @Override
    public Item getRequiredHelmetItem() {
        return ModItems.GOJO_BLINDFOLD;
    }

    @Override
    public int getDurationTicks() {
        // 270t of client-side cinematic plays after the server activates the domain,
        // plus 600t of actual active domain time = 870t total for ~30s of ACTIVE phase.
        return 870;
    }

    @Override
    public double getCaptureRadius() {
        return 10.0;
    }

    @Override
    public void tick(ServerWorld world, PlayerEntity caster, Set<UUID> captured) {
        // Caster buffs (refreshed every tick so they don't expire mid-domain)
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 1, false, false));
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 1, false, false));

        // Ambient particles: mystical blue-white motes (every 3 ticks)
        if (world.getTime() % 3 == 0) {
            Vec3d cp = caster.getPos().add(0, 1.0, 0);
            // SOUL_FIRE_FLAME slowly orbiting the caster (every 6 ticks)
            if (world.getTime() % 6 == 0) {
                double angle = world.getTime() * 0.18;
                for (int i = 0; i < 2; i++) {
                    double a = angle + i * Math.PI;
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            cp.x + Math.cos(a) * 1.6,
                            cp.y - 0.4 + Math.random() * 2.0,
                            cp.z + Math.sin(a) * 1.6,
                            0, 0, 0.04, 0, 1.0);
                }
            }
            // Pillar beams + ambient motes around each captured player
            double pillarPhase = world.getTime() * 0.018;
            for (UUID uuid2 : captured) {
                var t2 = world.getServer().getPlayerManager().getPlayer(uuid2);
                if (t2 == null || t2.isDead()) continue;
                Vec3d tp = t2.getPos();

                // --- Crystalline pillar: 5 rotating rings, 4 points each ---
                for (int layer = 0; layer <= 4; layer++) {
                    double ry = tp.y + layer * 0.65;
                    for (int j = 0; j < 4; j++) {
                        double a = j * Math.PI / 2 + pillarPhase;
                        world.spawnParticles(ParticleTypes.END_ROD,
                                tp.x + Math.cos(a) * 0.50, ry, tp.z + Math.sin(a) * 0.50,
                                0, 0, 0.006, 0, 1.0);
                    }
                }

                // --- Upward beam bursting from pillar top ---
                world.spawnParticles(ParticleTypes.END_ROD,
                        tp.x, tp.y + 2.9, tp.z, 0, 0, 0.20, 0, 1.0);
                world.spawnParticles(ParticleTypes.END_ROD,
                        tp.x, tp.y + 2.9, tp.z, 0, 0, 0.38, 0, 1.0);

                // --- Blue base glow (SOUL_FIRE_FLAME at feet) ---
                for (int j = 0; j < 2; j++) {
                    double a = j * Math.PI + pillarPhase * 0.6;
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            tp.x + Math.cos(a) * 0.50, tp.y + 0.05, tp.z + Math.sin(a) * 0.50,
                            0, 0.015, 0, 0, 1.0);
                }

                // --- Ambient motes drifting upward (sparse scatter) ---
                for (int i = 0; i < 2; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = 0.4 + Math.random() * 1.3;
                    world.spawnParticles(ParticleTypes.END_ROD,
                            tp.x + Math.cos(a) * r, tp.y + 0.5 + Math.random() * 2.5,
                            tp.z + Math.sin(a) * r,
                            0, 0.025 + Math.random() * 0.025, 0, 0, 1.0);
                }
            }
        }

        // Effects on captured players
        for (UUID uuid : captured) {
            var target = world.getServer().getPlayerManager().getPlayer(uuid);
            if (target == null || target.isDead()) continue;

            // Glowing for all captured
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false));

            // Unlimited Void specific: total sensory overload
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, false));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 40, 0, false, false));
            target.setFrozenTicks(Math.max(target.getFrozenTicks(), 300));
        }
    }

    @Override
    public void onActivate(ServerWorld world, PlayerEntity caster) {
        // Initial burst of caster buffs
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, getDurationTicks() + 60, 1, false, false));
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, getDurationTicks() + 60, 1, false, false));
    }

    @Override
    public void onExpire(ServerWorld world, PlayerEntity caster) {
        // Reversal cost — brief weakness after domain collapses
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, false, true));
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 0, false, true));
    }
}
