package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record DomainCastStartPayload(UUID casterUuid, String domainType) implements CustomPayload {

    public static final CustomPayload.Id<DomainCastStartPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "domain_cast_start"));

    public static final PacketCodec<RegistryByteBuf, DomainCastStartPayload> CODEC =
            PacketCodec.of(DomainCastStartPayload::encode, DomainCastStartPayload::decode);

    private static void encode(DomainCastStartPayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.casterUuid());
        buf.writeString(payload.domainType());
    }

    private static DomainCastStartPayload decode(RegistryByteBuf buf) {
        return new DomainCastStartPayload(buf.readUuid(), buf.readString());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
