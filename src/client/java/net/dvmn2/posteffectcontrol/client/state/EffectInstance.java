package net.dvmn2.posteffectcontrol.client.state;

import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.minecraft.util.Identifier;

/**
 * One active layer on the client. Time is measured with {@code System.nanoTime} (smoother than ticks).
 * Fields are written by {@link ActiveEffects} (same package) and read through getters. Client thread only.
 */
public final class EffectInstance {

    public static final long NEVER = Long.MAX_VALUE;

    private static final float NANOS_PER_MILLI = 1_000_000f;
    private static final long NANOS_PER_MILLI_LONG = 1_000_000L;

    final String key;
    final long startNanos;
    final float seed;
    final float[] params = new float[SyncPayload.PARAM_COUNT];

    String effectId = "";
    Identifier pipeline = Identifier.of("minecraft", "none");
    boolean custom;
    int priority;
    float intensity;
    int color;
    boolean flashing;
    boolean overHud;
    int fadeInMs;
    int fadeOutMs;
    long endNanos = NEVER;
    long removedNanos = 0L;

    EffectInstance(String key, long startNanos, float seed) {
        this.key = key;
        this.startNanos = startNanos;
        this.seed = seed;
    }

    public String getKey() {
        return key;
    }

    /**
     * Returns the short name of a built-in effect or {@code namespace:path} of a pack effect.
     */
    public String getEffectId() {
        return effectId;
    }

    /**
     * Returns the post_effect id loaded by the vanilla shader loader.
     */
    public Identifier getPipeline() {
        return pipeline;
    }

    public boolean isCustom() {
        return custom;
    }

    public float getIntensity() {
        return intensity;
    }

    public int getColor() {
        return color;
    }

    /**
     * Returns the 8 shader parameters p0..p7.
     */
    public float[] getParams() {
        return params;
    }

    public boolean isFlashing() {
        return flashing;
    }

    public boolean isOverHud() {
        return overHud;
    }

    public long getStartNanos() {
        return startNanos;
    }

    public float getSeed() {
        return seed;
    }

    /**
     * Whether the server already removed this layer (it is fading out).
     */
    public boolean isRemoved() {
        return removedNanos != 0L;
    }

    /**
     * Envelope 0..1: fade-in from the start, fade-out towards the end or towards the removal moment.
     */
    public float getEnvelope(long now) {
        float in = 1f;
        if (fadeInMs > 0) {
            in = clamp((now - startNanos) / (fadeInMs * NANOS_PER_MILLI));
        }
        float out = 1f;
        long fadeNanos = fadeOutMs * NANOS_PER_MILLI_LONG;
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

    /**
     * Whether the layer has faded out completely and will not come back.
     */
    public boolean isFinished(long now) {
        if (endNanos != NEVER && now >= endNanos) {
            return true;
        }
        if (removedNanos != 0L) {
            long fadeNanos = fadeOutMs * NANOS_PER_MILLI_LONG;
            return fadeNanos <= 0 || now - removedNanos >= fadeNanos;
        }
        return false;
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
