package net.dvmn2.posteffectcontrol.client.effect;

import net.minecraft.util.Identifier;

/**
 * Один активный слой на клиенте. Время считаем по System.nanoTime (плавнее тиков).
 * Только клиентский поток.
 */
public final class EffectInstance {

    public static final long NEVER = Long.MAX_VALUE;

    public final String key;
    /** Короткое имя встроенного эффекта или {@code ns:path} эффекта из ресурспака. */
    public String effectId;
    /** Идентификатор post_effect, который грузит ванильный ShaderLoader. */
    public Identifier pipeline;
    public boolean custom;
    /** Приоритет слоя от сервера: больший рисуется позже (выше). */
    public int priority;
    public float intensity;
    public int color;
    public final float[] params = new float[8];
    public boolean flashing;
    public boolean overHud;
    public int fadeInMs;
    public int fadeOutMs;
    public final long startNanos;
    public long endNanos = NEVER;
    /** 0 — слой ещё назначен; иначе момент, когда сервер его снял (идёт затухание). */
    public long removedNanos = 0L;
    public final float seed;

    public EffectInstance(String key, long startNanos, float seed) {
        this.key = key;
        this.startNanos = startNanos;
        this.seed = seed;
    }

    /** Огибающая 0..1: fade-in от старта, fade-out к концу или к моменту снятия. */
    public float envelope(long now) {
        float in = 1f;
        if (fadeInMs > 0) {
            in = clamp((now - startNanos) / (fadeInMs * 1_000_000f));
        }
        float out = 1f;
        long fadeNanos = fadeOutMs * 1_000_000L;
        if (removedNanos != 0L) {
            out = fadeNanos <= 0 ? 0f : 1f - clamp((now - removedNanos) / (float) fadeNanos);
        }
        if (endNanos != NEVER) {
            if (now >= endNanos) {
                out = 0f;
            } else if (fadeNanos > 0) {
                out = Math.min(out, clamp((endNanos - now) / (float) fadeNanos));
            }
        }
        float t = Math.min(in, out);
        return t * t * (3f - 2f * t);
    }

    /** Полностью погас и больше не вернётся. */
    public boolean finished(long now) {
        if (endNanos != NEVER && now >= endNanos) {
            return true;
        }
        if (removedNanos != 0L) {
            long fadeNanos = fadeOutMs * 1_000_000L;
            return fadeNanos <= 0 || now - removedNanos >= fadeNanos;
        }
        return false;
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
