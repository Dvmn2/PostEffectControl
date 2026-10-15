package net.dvmn2.posteffectcontrol.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload от мода к плагину (канал {@code dvmn2:pe_caps}): какие эффекты из ресурспаков
 * сейчас доступны клиенту. Шлётся после входа на сервер и после каждой перезагрузки ресурсов.
 * <pre>
 * VarInt protocol
 * VarInt count
 * count x {
 *   String id (ns:path), String displayName, Boolean flashing, Boolean overHud,
 *   Float defaultIntensity, Int defaultColorRgb,
 *   VarInt paramCount, paramCount x { String name, VarInt index, Float def, Float min, Float max }
 * }
 * </pre>
 */
public record CapsPayload(int protocol, List<Effect> effects) implements CustomPayload {

    public static final CustomPayload.Id<CapsPayload> ID =
            new CustomPayload.Id<>(Identifier.of("dvmn2", "pe_caps"));

    public static final int MAX_EFFECTS = 48;
    public static final int MAX_PARAMS = 8;
    /** Запас под лимит C2S plugin-message (32767 байт). */
    public static final int MAX_BYTES = 28_000;

    public record Param(String name, int index, float def, float min, float max) {
    }

    public record Effect(String id, String displayName, boolean flashing, boolean overHud,
                         float defaultIntensity, int defaultColor, List<Param> params) {
    }

    public static final PacketCodec<PacketByteBuf, CapsPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeVarInt(payload.protocol());
                buf.writeVarInt(payload.effects().size());
                for (Effect e : payload.effects()) {
                    buf.writeString(e.id());
                    buf.writeString(e.displayName());
                    buf.writeBoolean(e.flashing());
                    buf.writeBoolean(e.overHud());
                    buf.writeFloat(e.defaultIntensity());
                    buf.writeInt(e.defaultColor());
                    buf.writeVarInt(e.params().size());
                    for (Param p : e.params()) {
                        buf.writeString(p.name());
                        buf.writeVarInt(p.index());
                        buf.writeFloat(p.def());
                        buf.writeFloat(p.min());
                        buf.writeFloat(p.max());
                    }
                }
            },
            buf -> {
                int protocol = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_EFFECTS) {
                    throw new IllegalArgumentException("pe_caps: недопустимое количество эффектов: " + count);
                }
                List<Effect> list = new ArrayList<>(count);
                for (int n = 0; n < count; n++) {
                    String id = buf.readString(96);
                    String name = buf.readString(96);
                    boolean flashing = buf.readBoolean();
                    boolean overHud = buf.readBoolean();
                    float intensity = buf.readFloat();
                    int color = buf.readInt();
                    int pc = buf.readVarInt();
                    if (pc < 0 || pc > MAX_PARAMS) {
                        throw new IllegalArgumentException("pe_caps: недопустимое число параметров: " + pc);
                    }
                    List<Param> params = new ArrayList<>(pc);
                    for (int i = 0; i < pc; i++) {
                        params.add(new Param(buf.readString(32), buf.readVarInt(),
                                buf.readFloat(), buf.readFloat(), buf.readFloat()));
                    }
                    list.add(new Effect(id, name, flashing, overHud, intensity, color, List.copyOf(params)));
                }
                return new CapsPayload(protocol, List.copyOf(list));
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
