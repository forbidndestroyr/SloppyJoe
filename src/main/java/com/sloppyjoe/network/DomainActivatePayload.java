package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DomainActivatePayload(UUID casterUuid, List<UUID> capturedUuids, String domainType) implements CustomPayload {

    public static final CustomPayload.Id<DomainActivatePayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "domain_activate"));

    public static final PacketCodec<RegistryByteBuf, DomainActivatePayload> CODEC =
            PacketCodec.of(DomainActivatePayload::encode, DomainActivatePayload::decode);

    private static void encode(DomainActivatePayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.casterUuid());
        buf.writeInt(payload.capturedUuids().size());
        for (UUID uuid : payload.capturedUuids()) {
            buf.writeUuid(uuid);
        }
        buf.writeString(payload.domainType());
    }

    private static DomainActivatePayload decode(RegistryByteBuf buf) {
        UUID casterUuid = buf.readUuid();
        int count = buf.readInt();
        List<UUID> captured = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            captured.add(buf.readUuid());
        }
        String domainType = buf.readString();
        return new DomainActivatePayload(casterUuid, captured, domainType);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
