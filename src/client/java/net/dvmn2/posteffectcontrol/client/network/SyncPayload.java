package net.dvmn2.posteffectcontrol.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * S2C {@code dvmn2:pe_sync}: the COMPLETE layer set of the player. Every message replaces the previous one.
 * <pre>
 * VarInt protocol
 * VarInt count          (max 64)
 * count x {
 *   String  key          (max 96 characters)
 *   String  effect       (max 96 characters)
 *   Int     priority
 *   Float   intensity
 *   Int     colorRgb     (negative = not set)
 *   Float x8 params      (NaN = not set)
 *   Boolean infinite
 *   VarLong remainingMs
 *   VarInt  fadeInMs
 *   VarInt  fadeOutMs
 * }
 * </pre>
 * {@code effect} is the short name of a built-in effect ({@code freeze}) or {@code namespace:path} of an
 * effect from a resource pack. Layers arrive sorted by {@code priority} (lower is drawn first, higher is on
 * top); the client keeps the priority so fading-out removed layers stay in that order. Unset values (negative
 * color, NaN parameters) are replaced with the defaults from the effect description in the resource pack.
 * The flashing and over-HUD flags are not sent: the client decides them itself.
 */
public record SyncPayload(int protocol, List<Layer> layers) implements CustomPayload {

    public static final CustomPayload.Id<SyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "pe_sync"));

    public static final int PARAM_COUNT = 8;
    public static final int MAX_LAYERS = 64;
    public static final int MAX_KEY_LENGTH = 96;
    public static final int MAX_EFFECT_LENGTH = 96;

    public static final PacketCodec<PacketByteBuf, SyncPayload> CODEC = PacketCodec.of(
            SyncPayload::write,
            SyncPayload::read);

    /**
     * One layer of the set.
     */
    public record Layer(String key, String effect, int priority, float intensity, int color, float[] params,
                        boolean infinite, long remainingMs, int fadeInMs, int fadeOutMs) {

        public Layer {
            params = Arrays.copyOf(params, PARAM_COUNT);
        }

        @Override
        public float[] params() {
            return Arrays.copyOf(params, params.length);
        }
    }

    private static void write(SyncPayload payload, PacketByteBuf buf) {
        buf.writeVarInt(payload.protocol());
        buf.writeVarInt(payload.layers().size());
        for (Layer layer : payload.layers()) {
            buf.writeString(layer.key());
            buf.writeString(layer.effect());
            buf.writeInt(layer.priority());
            buf.writeFloat(layer.intensity());
            buf.writeInt(layer.color());
            for (int i = 0; i < PARAM_COUNT; i++) {
                buf.writeFloat(layer.params()[i]);
            }
            buf.writeBoolean(layer.infinite());
            buf.writeVarLong(layer.remainingMs());
            buf.writeVarInt(layer.fadeInMs());
            buf.writeVarInt(layer.fadeOutMs());
        }
    }

    private static SyncPayload read(PacketByteBuf buf) {
        try {
            int protocol = buf.readVarInt();
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_LAYERS) {
                throw new IllegalArgumentException("layer count out of range: " + count);
            }
            List<Layer> layers = new ArrayList<>(count);
            for (int n = 0; n < count; n++) {
                String key = buf.readString(MAX_KEY_LENGTH);
                String effect = buf.readString(MAX_EFFECT_LENGTH);
                int priority = buf.readInt();
                float intensity = buf.readFloat();
                int color = buf.readInt();
                float[] params = new float[PARAM_COUNT];
                for (int i = 0; i < PARAM_COUNT; i++) {
                    params[i] = buf.readFloat();
                }
                boolean infinite = buf.readBoolean();
                long remaining = buf.readVarLong();
                int fadeIn = buf.readVarInt();
                int fadeOut = buf.readVarInt();
                layers.add(new Layer(key, effect, priority, intensity, color, params, infinite, remaining,
                        fadeIn, fadeOut));
            }
            return new SyncPayload(protocol, List.copyOf(layers));
        } catch (RuntimeException ex) {
            buf.skipBytes(buf.readableBytes());
            return new SyncPayload(PostEffectControlProtocol.INVALID_PROTOCOL, List.of());
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
