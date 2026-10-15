package net.dvmn2.posteffectcontrol.client.effect;

import net.dvmn2.posteffectcontrol.PostEffectControl;
import net.dvmn2.posteffectcontrol.client.ClientConfig;
import net.dvmn2.posteffectcontrol.client.EffectSync;
import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Состояние активных слоёв. Только клиентский поток.
 * Порядок рисования = по возрастанию приоритета (при равном — порядок слоёв в pe_sync); затухающие снятые слои идут после новых слоёв того же приоритета.
 */
public final class ActiveEffects {

    private static final Pattern BUILTIN_ID = Pattern.compile("[a-z0-9_]{1,24}");
    /** Мигающие встроенные эффекты. Клиент решает это сам — сервер не может выдать мигание за безопасный эффект. */
    private static final Set<String> BUILTIN_FLASHING = Set.of("glitch");

    private static final Random RANDOM = new Random();
    private static final Map<String, EffectInstance> MAP = new LinkedHashMap<>();

    private ActiveEffects() {
    }

    public static void onSync(MinecraftClient client, SyncPayload payload) {
        long now = System.nanoTime();
        boolean allowPack = ClientConfig.get().allowPackEffects;
        Map<String, EffectInstance> next = new LinkedHashMap<>();
        for (SyncPayload.Layer l : payload.layers()) {
            String effect = l.effect();
            boolean custom = effect.indexOf(':') >= 0;
            Identifier pipeline;
            PackEffects.Meta meta = null;
            if (custom) {
                if (!allowPack) {
                    continue;
                }
                meta = PackEffects.get(client, effect);
                if (meta == null) {
                    EffectSync.fail(effect, "not found in client resources (resource pack is missing?)");
                    continue;
                }
                pipeline = meta.id();
            } else {
                if (!BUILTIN_ID.matcher(effect).matches()) {
                    continue; // id попадает в Identifier и в путь шейдера — принимаем только проверенные значения
                }
                pipeline = Identifier.of(PostEffectControl.MOD_ID, "fx/" + effect);
            }

            EffectInstance fx = MAP.get(l.key());
            if (fx == null || !fx.effectId.equals(effect)) {
                fx = new EffectInstance(l.key(), now, RANDOM.nextFloat() * 100f);
            }
            fx.effectId = effect;
            fx.pipeline = pipeline;
            fx.custom = custom;
            fx.priority = l.priority();

            float intensity = clamp01(l.intensity());
            if (meta != null) {
                intensity = clamp01(intensity * meta.defaultIntensity());
            }
            fx.intensity = intensity;
            fx.color = l.color() < 0 ? (meta != null ? meta.defaultColor() : 0xFFFFFF) : l.color() & 0xFFFFFF;
            for (int i = 0; i < fx.params.length; i++) {
                float v = l.params()[i];
                if (Float.isNaN(v)) {
                    v = meta != null ? meta.defaultParam(i) : 0f;
                }
                fx.params[i] = v;
            }
            fx.flashing = meta != null ? meta.flashing() : BUILTIN_FLASHING.contains(effect);
            fx.overHud = meta != null && meta.overHud();
            fx.fadeInMs = Math.max(0, Math.min(l.fadeInMs(), 60_000));
            fx.fadeOutMs = Math.max(0, Math.min(l.fadeOutMs(), 60_000));
            fx.endNanos = l.infinite() ? EffectInstance.NEVER : now + Math.min(l.remainingMs(), 31L * 86_400_000L) * 1_000_000L;
            fx.removedNanos = 0L;
            next.put(l.key(), fx);
        }
        // Снятые сервером: если уже догорели сами — выбрасываем, иначе плавно гасим.
        for (Map.Entry<String, EffectInstance> e : MAP.entrySet()) {
            if (next.containsKey(e.getKey())) {
                continue;
            }
            EffectInstance old = e.getValue();
            if (old.finished(now)) {
                continue;
            }
            if (old.removedNanos == 0L) {
                old.removedNanos = now;
            }
            next.put(e.getKey(), old);
        }
        // Порядок рисования задаёт приоритет (как на сервере); сортировка устойчивая: при равном приоритете
        // сохраняется порядок пакета, затухающие снятые слои остаются выше новых слоёв того же приоритета.
        List<EffectInstance> ordered = new ArrayList<>(next.values());
        ordered.sort(java.util.Comparator.comparingInt(fx -> fx.priority));
        MAP.clear();
        for (EffectInstance fx : ordered) {
            MAP.put(fx.key, fx);
        }
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 0f;
    }

    /** Копия для рендера; заодно чистит погасшие. */
    public static List<EffectInstance> snapshot() {
        long now = System.nanoTime();
        MAP.values().removeIf(fx -> fx.finished(now));
        return new ArrayList<>(MAP.values());
    }

    /** Слои, назначенные сервером (без затухающих снятых). */
    public static int assignedCount() {
        int n = 0;
        for (EffectInstance fx : MAP.values()) {
            if (fx.removedNanos == 0L) {
                n++;
            }
        }
        return n;
    }

    public static boolean isEmpty() {
        return MAP.isEmpty();
    }

    public static void clear() {
        MAP.clear();
    }
}
