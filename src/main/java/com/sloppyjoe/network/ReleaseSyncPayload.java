package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ReleaseSyncPayload() implements CustomPayload {

    public static final CustomPayload.Id<ReleaseSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "release_sync"));

    public static final PacketCodec<RegistryByteBuf, ReleaseSyncPayload> CODEC =
            PacketCodec.of((payload, buf) -> {}, buf -> new ReleaseSyncPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
