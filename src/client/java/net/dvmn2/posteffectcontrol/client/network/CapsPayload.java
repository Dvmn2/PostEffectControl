package net.dvmn2.posteffectcontrol.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * C2S {@code dvmn2:pe_caps}: the effects from the player's resource packs that are available on the client.
 * Sent only when the player allowed pack effects; after joining and after every resource reload.
 * <pre>
 * VarInt protocol
 * VarInt count             (max 48)
 * count x {
 *   String  id              (namespace:path, max 96 characters)
 *   String  displayName     (max 96 characters)
 *   Boolean flashing
 *   Boolean overHud
 *   Float   defaultIntensity
 *   Int     defaultColorRgb
 *   VarInt  paramCount      (max 8)
 *   paramCount x { String name (max 32), VarInt index, Float def, Float min, Float max }
 * }
 * </pre>
 */
public record CapsPayload(int protocol, List<Effect> effects) implements CustomPayload {

    public static final CustomPayload.Id<CapsPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "pe_caps"));

    public static final int MAX_EFFECTS = 48;
    public static final int MAX_PARAMS = 8;
    public static final int MAX_ID_LENGTH = 96;
    public static final int MAX_NAME_LENGTH = 96;
    public static final int MAX_PARAM_NAME_LENGTH = 32;

    /**
     * Budget under the limit of a C2S plugin message (32767 bytes).
     */
    public static final int MAX_BYTES = 28_000;

    public static final PacketCodec<PacketByteBuf, CapsPayload> CODEC = PacketCodec.of(
            CapsPayload::write,
            CapsPayload::read);

    /**
     * A named parameter of an effect.
     */
    public record Param(String name, int index, float def, float min, float max) {
    }

    /**
     * An effect found in a resource pack.
     */
    public record Effect(String id, String displayName, boolean flashing, boolean overHud,
                         float defaultIntensity, int defaultColor, List<Param> params) {
    }

    private static void write(CapsPayload payload, PacketByteBuf buf) {
        buf.writeVarInt(payload.protocol());
        buf.writeVarInt(payload.effects().size());
        for (Effect effect : payload.effects()) {
            buf.writeString(effect.id());
            buf.writeString(effect.displayName());
            buf.writeBoolean(effect.flashing());
            buf.writeBoolean(effect.overHud());
            buf.writeFloat(effect.defaultIntensity());
            buf.writeInt(effect.defaultColor());
            buf.writeVarInt(effect.params().size());
            for (Param param : effect.params()) {
                buf.writeString(param.name());
                buf.writeVarInt(param.index());
                buf.writeFloat(param.def());
                buf.writeFloat(param.min());
                buf.writeFloat(param.max());
            }
        }
    }

    private static CapsPayload read(PacketByteBuf buf) {
        try {
            int protocol = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_EFFECTS) {
                throw new IllegalArgumentException("effect count out of range: " + count);
            }
            List<Effect> effects = new ArrayList<>(count);
            for (int n = 0; n < count; n++) {
                String id = buf.readString(MAX_ID_LENGTH);
                String name = buf.readString(MAX_NAME_LENGTH);
                boolean flashing = buf.readBoolean();
                boolean overHud = buf.readBoolean();
                float intensity = buf.readFloat();
                int color = buf.readInt();
                int paramCount = buf.readVarInt();
                if (paramCount < 0 || paramCount > MAX_PARAMS) {
                    throw new IllegalArgumentException("parameter count out of range: " + paramCount);
                }
                List<Param> params = new ArrayList<>(paramCount);
                for (int i = 0; i < paramCount; i++) {
                    params.add(new Param(buf.readString(MAX_PARAM_NAME_LENGTH), buf.readVarInt(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat()));
                }
                effects.add(new Effect(id, name, flashing, overHud, intensity, color, List.copyOf(params)));
            }
            return new CapsPayload(protocol, List.copyOf(effects));
        } catch (RuntimeException ex) {
            buf.skipBytes(buf.readableBytes());
            return new CapsPayload(PostEffectControlProtocol.INVALID_PROTOCOL, List.of());
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
