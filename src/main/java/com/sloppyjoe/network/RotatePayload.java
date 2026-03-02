package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record RotatePayload(boolean clockwise) implements CustomPayload {

    public static final CustomPayload.Id<RotatePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "rotate"));

    public static final PacketCodec<RegistryByteBuf, RotatePayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> buf.writeBoolean(payload.clockwise()),
                    buf -> new RotatePayload(buf.readBoolean())
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
