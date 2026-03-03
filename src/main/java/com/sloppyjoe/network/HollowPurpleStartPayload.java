package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Sent server → client when Hollow Purple is successfully cast; triggers the cutscene. */
public record HollowPurpleStartPayload() implements CustomPayload {

    public static final CustomPayload.Id<HollowPurpleStartPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "hollow_purple_start"));

    public static final PacketCodec<RegistryByteBuf, HollowPurpleStartPayload> CODEC =
            PacketCodec.of((payload, buf) -> {}, buf -> new HollowPurpleStartPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
