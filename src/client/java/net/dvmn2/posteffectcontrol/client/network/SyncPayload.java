package net.dvmn2.posteffectcontrol.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload от плагина (канал {@code dvmn2:pe_sync}): ПОЛНЫЙ набор слоёв игрока.
 * Каждое новое сообщение заменяет предыдущее.
 * <pre>
 * VarInt protocol (= 3)
 * VarInt count
 * count x {
 *   String key, String effect, Int priority,
 *   Float intensity, Int colorRgb, Float x8 params,
 *   Boolean infinite, VarLong remainingMs, VarInt fadeInMs, VarInt fadeOutMs
 * }
 * </pre>
 * {@code effect}: короткое имя встроенного эффекта ({@code freeze}) или {@code ns:path} эффекта из ресурспака.
 * Слои приходят уже отсортированными по {@code priority} (меньший рисуется раньше, больший — выше);
 * клиент хранит приоритет, чтобы затухающие снятые слои не выпадали из этого порядка.
 * Незаданные значения: {@code color < 0} и {@code NaN} в параметрах — клиент подставит значения по умолчанию
 * из описания эффекта в ресурспаке. Флаги flashing/over_hud по сети не передаются: их определяет клиент.
 */
public record SyncPayload(int protocol, List<Layer> layers) implements CustomPayload {

    public static final CustomPayload.Id<SyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "pe_sync"));

    public static final int PARAM_COUNT = 8;
    /** Защита от испорченного пакета. */
    public static final int MAX_LAYERS = 64;

    public record Layer(String key, String effect, int priority, float intensity, int color, float[] params,
                        boolean infinite, long remainingMs, int fadeInMs, int fadeOutMs) {
    }

    public static final PacketCodec<PacketByteBuf, SyncPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.protocol());
                buf.writeVarInt(payload.layers().size());
                for (Layer l : payload.layers()) {
                    buf.writeString(l.key());
                    buf.writeString(l.effect());
                    buf.writeInt(l.priority());
                    buf.writeFloat(l.intensity());
                    buf.writeInt(l.color());
                    for (int i = 0; i < PARAM_COUNT; i++) {
                        buf.writeFloat(l.params()[i]);
                    }
                    buf.writeBoolean(l.infinite());
                    buf.writeVarLong(l.remainingMs());
                    buf.writeVarInt(l.fadeInMs());
                    buf.writeVarInt(l.fadeOutMs());
                }
            },
            buf -> {
                int protocol = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_LAYERS) {
                    throw new IllegalArgumentException("pe_sync: недопустимое количество слоёв: " + count);
                }
                List<Layer> layers = new ArrayList<>(count);
                for (int n = 0; n < count; n++) {
                    String key = buf.readString(96);
                    String effect = buf.readString(96);
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
                    layers.add(new Layer(key, effect, priority, intensity, color, params, infinite, remaining, fadeIn, fadeOut));
                }
                return new SyncPayload(protocol, List.copyOf(layers));
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
