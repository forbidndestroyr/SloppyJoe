package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record DomainEnterPayload(UUID playerUuid) implements CustomPayload {

    public static final CustomPayload.Id<DomainEnterPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "domain_enter"));

    public static final PacketCodec<RegistryByteBuf, DomainEnterPayload> CODEC =
            PacketCodec.of(DomainEnterPayload::encode, DomainEnterPayload::decode);

    private static void encode(DomainEnterPayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.playerUuid());
    }

    private static DomainEnterPayload decode(RegistryByteBuf buf) {
        return new DomainEnterPayload(buf.readUuid());
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
