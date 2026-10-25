package net.dvmn2.posteffectcontrol.client.state;

import net.dvmn2.posteffectcontrol.client.PostEffectControlClient;
import net.dvmn2.posteffectcontrol.client.PostEffectControlConfig;
import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * State of the active layers. Client thread only.
 * <p>
 * Drawing order is ascending priority (with equal priority, the order of layers in the message); fading-out
 * removed layers go after new layers of the same priority.
 */
public final class ActiveEffects {

    private static final Pattern BUILTIN_ID = Pattern.compile("[a-z0-9_]{1,24}");

    /**
     * Built-in effects that flash. The client decides this itself: a server cannot present a flashing effect
     * as a safe one.
     */
    private static final Set<String> BUILTIN_FLASHING = Set.of("glitch");

    private static final int MAX_FADE_MS = 60_000;
    private static final long MAX_REMAINING_MS = 31L * 86_400_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final float SEED_RANGE = 100f;
    private static final int RGB_MASK = 0xFFFFFF;

    private static final Random RANDOM = new Random();
    private static final Map<String, EffectInstance> MAP = new LinkedHashMap<>();

    private ActiveEffects() {
    }

    /**
     * Replaces the layer set with the one from the server.
     */
    public static void apply(MinecraftClient client, SyncPayload payload) {
        long now = System.nanoTime();
        boolean allowPack = PostEffectControlConfig.get().allowPackEffects;
        Map<String, EffectInstance> next = new LinkedHashMap<>();
        for (SyncPayload.Layer layer : payload.layers()) {
            EffectInstance fx = buildInstance(client, layer, now, allowPack);
            if (fx != null) {
                next.put(layer.key(), fx);
            }
        }
        // Layers the server removed: dropped when they already ended, otherwise they fade out smoothly.
        for (Map.Entry<String, EffectInstance> entry : MAP.entrySet()) {
            if (next.containsKey(entry.getKey())) {
                continue;
            }
            EffectInstance old = entry.getValue();
            if (old.isFinished(now)) {
                continue;
            }
            if (old.removedNanos == 0L) {
                old.removedNanos = now;
            }
            next.put(entry.getKey(), old);
        }
        // The sort is stable: with equal priority the message order is kept.
        List<EffectInstance> ordered = new ArrayList<>(next.values());
        ordered.sort(Comparator.comparingInt(fx -> fx.priority));
        MAP.clear();
        for (EffectInstance fx : ordered) {
            MAP.put(fx.key, fx);
        }
    }

    /**
     * Returns a copy for rendering and drops layers that have faded out.
     */
    public static List<EffectInstance> getSnapshot() {
        long now = System.nanoTime();
        MAP.values().removeIf(fx -> fx.isFinished(now));
        return new ArrayList<>(MAP.values());
    }

    /**
     * Returns the layers assigned by the server (without fading-out removed ones).
     */
    public static int getAssignedCount() {
        int count = 0;
        for (EffectInstance fx : MAP.values()) {
            if (fx.removedNanos == 0L) {
                count++;
            }
        }
        return count;
    }

    public static boolean isEmpty() {
        return MAP.isEmpty();
    }

    public static void clear() {
        MAP.clear();
    }

    private static @Nullable EffectInstance buildInstance(MinecraftClient client, SyncPayload.Layer layer,
                                                          long now, boolean allowPack) {
        String effect = layer.effect();
        boolean custom = effect.indexOf(':') >= 0;
        Identifier pipeline;
        PackEffects.Meta meta = null;
        if (custom) {
            if (!allowPack) {
                return null;
            }
            meta = PackEffects.find(client, effect);
            if (meta == null) {
                EffectFailures.fail(effect, "not found in client resources (resource pack is missing?)");
                return null;
            }
            pipeline = meta.id();
        } else {
            // The id goes into an Identifier and a shader path: only checked values are accepted.
            if (!BUILTIN_ID.matcher(effect).matches()) {
                return null;
            }
            pipeline = Identifier.of(PostEffectControlClient.MOD_ID, "fx/" + effect);
        }

        EffectInstance fx = MAP.get(layer.key());
        if (fx == null || !fx.effectId.equals(effect)) {
            fx = new EffectInstance(layer.key(), now, RANDOM.nextFloat() * SEED_RANGE);
        }
        fx.effectId = effect;
        fx.pipeline = pipeline;
        fx.custom = custom;
        fx.priority = layer.priority();

        float intensity = clamp01(layer.intensity());
        if (meta != null) {
            intensity = clamp01(intensity * meta.defaultIntensity());
        }
        fx.intensity = intensity;
        fx.color = layer.color() < 0 ? (meta != null ? meta.defaultColor() : RGB_MASK) : layer.color() & RGB_MASK;
        float[] params = layer.params();
        for (int i = 0; i < fx.params.length; i++) {
            float value = params[i];
            if (!Float.isFinite(value)) {
                value = meta != null ? meta.defaultParam(i) : 0f;
            }
            fx.params[i] = value;
        }
        fx.flashing = meta != null ? meta.flashing() : BUILTIN_FLASHING.contains(effect);
        fx.overHud = meta != null && meta.overHud();
        fx.fadeInMs = Math.max(0, Math.min(layer.fadeInMs(), MAX_FADE_MS));
        fx.fadeOutMs = Math.max(0, Math.min(layer.fadeOutMs(), MAX_FADE_MS));
        long remaining = Math.max(0L, Math.min(layer.remainingMs(), MAX_REMAINING_MS));
        fx.endNanos = layer.infinite() ? EffectInstance.NEVER : now + remaining * NANOS_PER_MILLI;
        fx.removedNanos = 0L;
        return fx;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
    }
}
