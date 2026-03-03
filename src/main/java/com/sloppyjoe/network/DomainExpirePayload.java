package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record DomainExpirePayload(UUID casterUuid) implements CustomPayload {

    public static final CustomPayload.Id<DomainExpirePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "domain_expire"));

    public static final PacketCodec<RegistryByteBuf, DomainExpirePayload> CODEC =
            PacketCodec.of(DomainExpirePayload::encode, DomainExpirePayload::decode);

    private static void encode(DomainExpirePayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.casterUuid());
    }

    private static DomainExpirePayload decode(RegistryByteBuf buf) {
        return new DomainExpirePayload(buf.readUuid());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
