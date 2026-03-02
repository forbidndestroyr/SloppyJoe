package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record GlassesEquipPayload(UUID playerUuid, boolean equipped) implements CustomPayload {

    public static final CustomPayload.Id<GlassesEquipPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "glasses_equip"));

    public static final PacketCodec<RegistryByteBuf, GlassesEquipPayload> CODEC =
            PacketCodec.of(GlassesEquipPayload::encode, GlassesEquipPayload::decode);

    private static void encode(GlassesEquipPayload payload, RegistryByteBuf buf) {
        buf.writeUuid(payload.playerUuid());
        buf.writeBoolean(payload.equipped());
    }

    private static GlassesEquipPayload decode(RegistryByteBuf buf) {
        UUID uuid = buf.readUuid();
        boolean equipped = buf.readBoolean();
        return new GlassesEquipPayload(uuid, equipped);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
