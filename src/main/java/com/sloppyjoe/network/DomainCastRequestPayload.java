package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DomainCastRequestPayload() implements CustomPayload {

    public static final CustomPayload.Id<DomainCastRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "domain_cast_request"));

    public static final PacketCodec<RegistryByteBuf, DomainCastRequestPayload> CODEC =
            PacketCodec.of((payload, buf) -> {}, buf -> new DomainCastRequestPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
