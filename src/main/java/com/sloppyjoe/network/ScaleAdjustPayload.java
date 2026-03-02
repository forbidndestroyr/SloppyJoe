package com.sloppyjoe.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ScaleAdjustPayload(boolean increase, int axis) implements CustomPayload {
    // axis: 0=uniform, 1=X, 2=Y, 3=Z

    public static final CustomPayload.Id<ScaleAdjustPayload> ID =
            new CustomPayload.Id<>(Identifier.of("sloppyjoe", "scale_adjust"));

    public static final PacketCodec<RegistryByteBuf, ScaleAdjustPayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> {
                        buf.writeBoolean(payload.increase());
                        buf.writeByte(payload.axis());
                    },
                    buf -> new ScaleAdjustPayload(buf.readBoolean(), buf.readByte())
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
