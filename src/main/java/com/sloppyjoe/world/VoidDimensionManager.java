package com.sloppyjoe.world;

import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages teleportation into and out of the Unlimited Void dimension.
 *
 * Flow:
 *   Domain activates → caster + captured players teleport to the void dimension
 *   Player right-clicks orb → teleports voluntarily into the running domain
 *   Domain expires → all involved players return to their saved origin positions
 */
public class VoidDimensionManager {

    /** Registry key for the data-driven dimension at data/sloppyjoe/dimension/unlimited_void.json */
    public static final RegistryKey<World> VOID_KEY =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of("sloppyjoe", "unlimited_void"));

    /** Platform center — barrier floor at Y=64, players spawn at Y=65. */
    private static final double PLATFORM_X = 0.5;
    private static final double PLATFORM_Y = 64.0;
    private static final double PLATFORM_Z = 0.5;
    private static final int    PLATFORM_RADIUS = 16;

    /** UUID → pre-domain position/world so we can return players on domain expire. */
    private static final Map<UUID, OriginPos> origins = new HashMap<>();

    // -----------------------------------------------------------------------
    // Domain lifecycle hooks (called from DomainManager)
    // -----------------------------------------------------------------------

    /** Called when the domain activates — teleports caster and all captured players in. */
    public static void onDomainActivate(MinecraftServer server, ServerPlayerEntity caster, Set<UUID> captured) {
        ServerWorld voidWorld = server.getWorld(VOID_KEY);
        if (voidWorld == null) {
            server.sendMessage(net.minecraft.text.Text.literal(
                    "[SloppyJoe] Warning: unlimited_void dimension not found. Skipping teleport."));
            return;
        }

        ensureBarrierPlatform(voidWorld);

        teleportPlayerIn(caster, voidWorld);
        for (UUID uuid : captured) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) teleportPlayerIn(player, voidWorld);
        }
    }

    /** Called when the domain expires — returns everyone to their saved origin. */
    public static void onDomainExpire(MinecraftServer server, UUID casterUuid, Set<UUID> capturedUuids) {
        ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterUuid);
        if (caster != null) teleportPlayerOut(caster, server);
        origins.remove(casterUuid);

        for (UUID uuid : capturedUuids) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) teleportPlayerOut(player, server);
            origins.remove(uuid);
        }
    }

    // -----------------------------------------------------------------------
    // Public utilities (orb right-click, etc.)
    // -----------------------------------------------------------------------

    /** Teleports a player into the void dimension. Saves their origin. */
    public static void teleportPlayerIn(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerWorld voidWorld = server.getWorld(VOID_KEY);
        if (voidWorld == null) return;
        ensureBarrierPlatform(voidWorld);
        teleportPlayerIn(player, voidWorld);
    }

    public static boolean isInVoidDimension(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey().equals(VOID_KEY);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private static void teleportPlayerIn(ServerPlayerEntity player, ServerWorld voidWorld) {
        // Save origin only once (putIfAbsent prevents overwriting if somehow called twice)
        origins.putIfAbsent(player.getUuid(), new OriginPos(
                player.getWorld().getRegistryKey(),
                player.getPos(),
                player.getYaw(),
                player.getPitch()
        ));

        player.teleport(voidWorld,
                PLATFORM_X, PLATFORM_Y + 1.0, PLATFORM_Z,
                new HashSet<>(),
                player.getYaw(), player.getPitch(),
                true);
    }

    private static void teleportPlayerOut(ServerPlayerEntity player, MinecraftServer server) {
        OriginPos origin = origins.remove(player.getUuid());
        if (origin == null) return;

        ServerWorld dest = server.getWorld(origin.worldKey());
        if (dest == null) dest = server.getOverworld();

        player.teleport(dest,
                origin.pos().x, origin.pos().y, origin.pos().z,
                new HashSet<>(),
                origin.yaw(), origin.pitch(),
                true);
    }

    /**
     * Places a platform of BARRIER blocks at Y=64, (0,0) ± PLATFORM_RADIUS.
     * Only runs once per world load (skips if the centre block is already a barrier).
     */
    private static void ensureBarrierPlatform(ServerWorld voidWorld) {
        BlockPos centre = new BlockPos((int) PLATFORM_X, (int) PLATFORM_Y, (int) PLATFORM_Z);
        if (voidWorld.getBlockState(centre).isOf(Blocks.BARRIER)) return;

        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                voidWorld.setBlockState(centre.add(dx, 0, dz), Blocks.BARRIER.getDefaultState());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Origin record
    // -----------------------------------------------------------------------

    private record OriginPos(RegistryKey<World> worldKey, Vec3d pos, float yaw, float pitch) {}
}
