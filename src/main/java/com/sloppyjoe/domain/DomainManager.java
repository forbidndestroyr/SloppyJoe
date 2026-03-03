package com.sloppyjoe.domain;

import com.sloppyjoe.entity.DomainOrbEntity;
import com.sloppyjoe.entity.ModEntities;
import com.sloppyjoe.network.DomainActivatePayload;
import com.sloppyjoe.network.DomainCastStartPayload;
import com.sloppyjoe.network.DomainExpirePayload;
import com.sloppyjoe.world.VoidDimensionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Server-side manager for all active domain expansions.
 * Registered on ServerTickEvents.END_SERVER_TICK in SloppyJoeMod.
 */
public class DomainManager {

    private static final int CAST_TICKS = 60; // 3 seconds

    /** Players currently in the 3s cast wind-up. */
    private static final Map<UUID, CastState> casting = new HashMap<>();

    /** Players with an active domain. */
    private static final Map<UUID, ActiveDomain> active = new HashMap<>();

    /** Players currently in a cutscene — immune to damage. */
    public static final Set<UUID> cutscenePlayers = new HashSet<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Called from the C2S DomainCastRequestPayload handler. */
    public static void beginCast(ServerPlayerEntity caster) {
        UUID uuid = caster.getUuid();
        if (casting.containsKey(uuid) || active.containsKey(uuid)) return;

        // Determine which domain the player can cast based on helmet
        DomainExpansion domain = getDomainForCaster(caster);
        if (domain == null) return;

        casting.put(uuid, new CastState(domain, CAST_TICKS));

        // Broadcast cast start so all clients freeze the caster
        caster.getServer().getPlayerManager().sendToAll(
                ServerPlayNetworking.createS2CPacket(new DomainCastStartPayload(uuid, domain.getId())));
    }

    /** Called every server tick (registered via ServerTickEvents.END_SERVER_TICK). */
    public static void tick(MinecraftServer server) {
        tickCasting(server);
        tickActive(server);
    }

    // -----------------------------------------------------------------------
    // Internal tick logic
    // -----------------------------------------------------------------------

    private static void tickCasting(MinecraftServer server) {
        Iterator<Map.Entry<UUID, CastState>> it = casting.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, CastState> entry = it.next();
            UUID uuid = entry.getKey();
            CastState state = entry.getValue();

            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(uuid);
            if (caster == null || caster.isDead()) {
                it.remove();
                continue;
            }

            state.ticksRemaining--;
            if (state.ticksRemaining <= 0) {
                it.remove();
                activate(server, caster, state.domain);
            }
        }
    }

    private static void tickActive(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveDomain>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ActiveDomain> entry = it.next();
            UUID uuid = entry.getKey();
            ActiveDomain ad = entry.getValue();

            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(uuid);

            // Expire if caster is gone or dead, or timer elapsed
            if (caster == null || caster.isDead() || ad.ticksRemaining <= 0) {
                expire(server, uuid, ad, caster);
                it.remove();
                continue;
            }

            ad.ticksRemaining--;
            ad.domain.tick((ServerWorld) caster.getWorld(), caster, ad.capturedUuids);
        }
    }

    private static void activate(MinecraftServer server, ServerPlayerEntity caster, DomainExpansion domain) {
        UUID casterUuid = caster.getUuid();
        ServerWorld world = (ServerWorld) caster.getWorld();

        // Capture nearby players
        Set<UUID> captured = new HashSet<>();
        double radius = domain.getCaptureRadius();
        for (ServerPlayerEntity nearby : server.getPlayerManager().getPlayerList()) {
            if (nearby.getUuid().equals(casterUuid)) continue;
            if (nearby.squaredDistanceTo(caster) <= radius * radius) {
                captured.add(nearby.getUuid());
                cutscenePlayers.add(nearby.getUuid());
            }
        }
        cutscenePlayers.add(casterUuid);

        // Spawn domain orb at caster position
        DomainOrbEntity orb = ModEntities.DOMAIN_ORB.create(world, SpawnReason.COMMAND);
        if (orb != null) {
            orb.setPosition(caster.getPos());
            orb.setCasterUuid(casterUuid);
            orb.setDomainType(domain.getId());
            for (UUID cap : captured) orb.addCaptured(cap);
            world.spawnEntity(orb);
        }

        // Warden sonic boom sound + sonic boom particles in a ring
        world.playSound(null, caster.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE,
                2.0f, 1.0f);
        spawnSonicBoomRing(world, caster.getPos(), 10.0);
        spawnActivationBurst(world, caster.getPos().add(0, 1.0, 0));

        // Run domain's own onActivate hook
        domain.onActivate(world, caster);

        // Store active domain state
        active.put(casterUuid, new ActiveDomain(domain, domain.getDurationTicks(), captured, orb));

        // Teleport caster + captured players into the void dimension
        VoidDimensionManager.onDomainActivate(server, caster, captured);

        // Broadcast to all clients
        List<UUID> capturedList = new ArrayList<>(captured);
        server.getPlayerManager().sendToAll(
                ServerPlayNetworking.createS2CPacket(
                        new DomainActivatePayload(casterUuid, capturedList, domain.getId())));
    }

    private static void expire(MinecraftServer server, UUID casterUuid,
                               ActiveDomain ad, ServerPlayerEntity caster) {
        // Remove orb
        if (ad.orb != null && !ad.orb.isRemoved()) {
            ad.orb.discard();
        }

        // Clear cutscene immunity for everyone involved
        cutscenePlayers.remove(casterUuid);
        cutscenePlayers.removeAll(ad.capturedUuids);

        // Clear frozen ticks for captured players
        for (UUID uuid : ad.capturedUuids) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(uuid);
            if (target != null) {
                target.setFrozenTicks(0);
                target.removeStatusEffect(StatusEffects.SLOWNESS);
                target.removeStatusEffect(StatusEffects.NAUSEA);
                target.removeStatusEffect(StatusEffects.GLOWING);
            }
        }

        if (caster != null) {
            ad.domain.onExpire((ServerWorld) caster.getWorld(), caster);
            spawnCollapseScatter((ServerWorld) caster.getWorld(), caster.getPos().add(0, 1.0, 0));
        }

        // Return everyone to their overworld origins
        VoidDimensionManager.onDomainExpire(server, casterUuid, ad.capturedUuids);

        // Notify all clients to stop rendering the domain
        server.getPlayerManager().sendToAll(
                ServerPlayNetworking.createS2CPacket(new DomainExpirePayload(casterUuid)));
    }

    /** END_ROD starburst + FLASH at activation moment. */
    private static void spawnActivationBurst(ServerWorld world, Vec3d center) {
        for (int i = 0; i < 60; i++) {
            double yaw   = Math.random() * Math.PI * 2;
            double pitch = (Math.random() - 0.5) * Math.PI;
            double speed = 0.25 + Math.random() * 0.5;
            double vx    = Math.cos(yaw) * Math.cos(pitch) * speed;
            double vy    = Math.sin(pitch) * speed;
            double vz    = Math.sin(yaw) * Math.cos(pitch) * speed;
            world.spawnParticles(ParticleTypes.END_ROD,
                    center.x, center.y, center.z, 0, vx, vy, vz, 1.0);
        }
        world.spawnParticles(ParticleTypes.FLASH,
                center.x, center.y, center.z, 4, 0.4, 0.4, 0.4, 0.0);
    }

    /** CLOUD + END_ROD scatter outward when domain collapses. */
    private static void spawnCollapseScatter(ServerWorld world, Vec3d center) {
        for (int i = 0; i < 35; i++) {
            double yaw   = Math.random() * Math.PI * 2;
            double pitch = (Math.random() - 0.5) * Math.PI;
            double speed = 0.1 + Math.random() * 0.3;
            double vx    = Math.cos(yaw) * Math.cos(pitch) * speed;
            double vy    = Math.sin(pitch) * speed + 0.05;
            double vz    = Math.sin(yaw) * Math.cos(pitch) * speed;
            world.spawnParticles(ParticleTypes.CLOUD,
                    center.x, center.y, center.z, 0, vx, vy, vz, 1.0);
        }
        for (int i = 0; i < 25; i++) {
            double yaw = Math.random() * Math.PI * 2;
            double r   = Math.random() * 3.0;
            world.spawnParticles(ParticleTypes.END_ROD,
                    center.x + Math.cos(yaw) * r,
                    center.y + (Math.random() - 0.3) * 2.0,
                    center.z + Math.sin(yaw) * r,
                    0, 0, 0.08, 0, 1.0);
        }
    }

    private static void spawnSonicBoomRing(ServerWorld world, Vec3d center, double radius) {
        int count = 24;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double x = center.x + radius * Math.cos(angle);
            double z = center.z + radius * Math.sin(angle);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, x, center.y + 1.0, z, 1, 0, 0, 0, 0);
        }
    }

    private static DomainExpansion getDomainForCaster(ServerPlayerEntity caster) {
        var helmet = caster.getInventory().getArmorStack(3); // index 3 = head
        for (DomainExpansion domain : REGISTRY) {
            if (helmet.isOf(domain.getRequiredHelmetItem())) {
                return domain;
            }
        }
        return null;
    }

    /** True if the given UUID currently has an active domain expansion. */
    public static boolean isActiveCaster(java.util.UUID uuid) {
        return active.containsKey(uuid);
    }

    // -----------------------------------------------------------------------
    // Domain registry
    // -----------------------------------------------------------------------

    private static final List<DomainExpansion> REGISTRY = new ArrayList<>();

    static {
        REGISTRY.add(UnlimitedVoidDomain.INSTANCE);
    }

    // -----------------------------------------------------------------------
    // Inner state classes
    // -----------------------------------------------------------------------

    private static class CastState {
        final DomainExpansion domain;
        int ticksRemaining;

        CastState(DomainExpansion domain, int ticks) {
            this.domain = domain;
            this.ticksRemaining = ticks;
        }
    }

    private static class ActiveDomain {
        final DomainExpansion domain;
        int ticksRemaining;
        final Set<UUID> capturedUuids;
        final DomainOrbEntity orb;

        ActiveDomain(DomainExpansion domain, int ticks, Set<UUID> captured, DomainOrbEntity orb) {
            this.domain = domain;
            this.ticksRemaining = ticks;
            this.capturedUuids = captured;
            this.orb = orb;
        }
    }
}
