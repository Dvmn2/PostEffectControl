package net.dvmn2.posteffectcontrol.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S {@code dvmn2:pe_status}: how effects are doing on the client. Informational for server admins only.
 * <pre>
 * VarInt  protocol
 * VarInt  state          (0 = working, 1 = ready, 2 = failed)
 * String  message        (max 512 characters; technical text in English)
 * Boolean iris           (Iris is installed)
 * Boolean rendering      (effects are enabled by the player)
 * VarInt  activeLayers
 * </pre>
 */
public record StatusPayload(int protocol, State state, String message, boolean iris, boolean rendering,
                            int activeLayers) implements CustomPayload {

    public static final CustomPayload.Id<StatusPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "pe_status"));

    public static final int MAX_MESSAGE_LENGTH = 512;

    public static final PacketCodec<PacketByteBuf, StatusPayload> CODEC = PacketCodec.of(
            StatusPayload::write,
            StatusPayload::read);

    /**
     * Reported state; the id is the wire value.
     */
    public enum State {
        WORKING(0),
        READY(1),
        FAILED(2);

        private final int id;

        State(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        static State fromId(int id) {
            for (State state : values()) {
                if (state.id == id) {
                    return state;
                }
            }
            throw new IllegalArgumentException("unknown state " + id);
        }
    }

    private static void write(StatusPayload payload, PacketByteBuf buf) {
        buf.writeVarInt(payload.protocol());
        buf.writeVarInt(payload.state().getId());
        buf.writeString(payload.message(), MAX_MESSAGE_LENGTH);
        buf.writeBoolean(payload.iris());
        buf.writeBoolean(payload.rendering());
        buf.writeVarInt(payload.activeLayers());
    }

    private static StatusPayload read(PacketByteBuf buf) {
        try {
            return new StatusPayload(buf.readVarInt(), State.fromId(buf.readVarInt()),
                    buf.readString(MAX_MESSAGE_LENGTH), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        } catch (RuntimeException ex) {
            buf.skipBytes(buf.readableBytes());
            return new StatusPayload(PostEffectControlProtocol.INVALID_PROTOCOL, State.FAILED, "", false, false, 0);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
