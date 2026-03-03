package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S: player pressed the Hollow Purple keybind. */
public record HollowPurpleCastPayload() implements CustomPayload {

    public static final CustomPayload.Id<HollowPurpleCastPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "hollow_purple_cast"));

    public static final PacketCodec<RegistryByteBuf, HollowPurpleCastPayload> CODEC =
            PacketCodec.of((payload, buf) -> {}, buf -> new HollowPurpleCastPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
