package net.dvmn2.posteffectcontrol.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Payload от мода к плагину (канал {@code dvmn2:pe_status}).
 * Формат: VarInt protocol, VarInt state, String message, Boolean iris, Boolean rendering, VarInt activeLayers.
 */
public record StatusPayload(int protocol, int state, String message, boolean iris, boolean rendering, int activeLayers)
        implements CustomPayload {

    public static final int STATE_WORKING = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_FAILED = 2;

    public static final CustomPayload.Id<StatusPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "pe_status"));

    public static final PacketCodec<PacketByteBuf, StatusPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.protocol());
                buf.writeVarInt(payload.state());
                buf.writeString(payload.message());
                buf.writeBoolean(payload.iris());
                buf.writeBoolean(payload.rendering());
                buf.writeVarInt(payload.activeLayers());
            },
            buf -> new StatusPayload(buf.readVarInt(), buf.readVarInt(), buf.readString(512),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
