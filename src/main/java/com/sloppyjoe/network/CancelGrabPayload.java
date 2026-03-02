package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record CancelGrabPayload() implements CustomPayload {

    public static final CustomPayload.Id<CancelGrabPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "cancel_grab"));

    public static final PacketCodec<RegistryByteBuf, CancelGrabPayload> CODEC =
            PacketCodec.of((payload, buf) -> {}, buf -> new CancelGrabPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
