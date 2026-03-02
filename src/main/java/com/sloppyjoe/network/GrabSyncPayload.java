package com.sloppyjoe.network;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record GrabSyncPayload(
        boolean isBlock,
        int blockStateId,
        Identifier entityTypeId,
        NbtCompound entityNbt,
        double grabDistance
) implements CustomPayload {

    public static final CustomPayload.Id<GrabSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "grab_sync"));

    public static final PacketCodec<RegistryByteBuf, GrabSyncPayload> CODEC =
            PacketCodec.of(GrabSyncPayload::encode, GrabSyncPayload::decode);

    private static void encode(GrabSyncPayload payload, RegistryByteBuf buf) {
        buf.writeBoolean(payload.isBlock());
        buf.writeInt(payload.blockStateId());
        buf.writeIdentifier(payload.entityTypeId());
        buf.writeNbt(payload.entityNbt());
        buf.writeDouble(payload.grabDistance());
    }

    private static GrabSyncPayload decode(RegistryByteBuf buf) {
        boolean isBlock = buf.readBoolean();
        int blockStateId = buf.readInt();
        Identifier entityTypeId = buf.readIdentifier();
        NbtCompound entityNbt = buf.readNbt();
        double grabDistance = buf.readDouble();
        return new GrabSyncPayload(isBlock, blockStateId, entityTypeId, entityNbt == null ? new NbtCompound() : entityNbt, grabDistance);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
