package net.dvmn2.posteffectcontrol.client.render;

import net.dvmn2.posteffectcontrol.client.PostEffectControlConfig;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.dvmn2.posteffectcontrol.client.state.ActiveEffects;
import net.dvmn2.posteffectcontrol.client.state.EffectFailures;
import net.dvmn2.posteffectcontrol.client.state.EffectInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.memory.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws the active layers over the world (before the HUD) or over everything (the over-HUD flag).
 * <p>
 * Pack effects (id {@code ns:path}) are loaded by the same vanilla shader loader as built-in ones. Before the
 * first use an effect is checked by {@link EffectValidator}. Client (render) thread only.
 */
public final class EffectRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("posteffectcontrol");
    private static final int POOL_LIFESPAN = 3;
    private static final float MIN_VISIBLE_INTENSITY = 0.002f;

    private static final ObjectPool POOL = new ObjectPool(POOL_LIFESPAN);

    /**
     * Effects that failed while rendering; skipped until the next shader reload.
     */
    private static final Set<String> RUNTIME_FAILED = new HashSet<>();

    private static boolean broken;

    private EffectRenderer() {
    }

    /**
     * Called after every shader reload: cached checks and errors are outdated.
     */
    public static void onShadersReloaded() {
        EffectValidator.reset();
        RUNTIME_FAILED.clear();
        EffectFailures.clear();
        PackEffects.markDirty();
    }

    /**
     * Draws the layers of one render stage. If anything unexpected goes wrong, an error is logged once and
     * drawing stays off until the game restarts, instead of breaking every frame.
     */
    public static void render(MinecraftClient client, boolean overHud) {
        if (broken) {
            return;
        }
        try {
            renderLayers(client, overHud);
        } catch (RuntimeException ex) {
            broken = true;
            LOGGER.error("Effect rendering failed and is disabled until the game restarts", ex);
        }
    }

    private static void renderLayers(MinecraftClient client, boolean overHud) {
        PostEffectControlConfig config = PostEffectControlConfig.get();
        if (!config.enabled || client.world == null || ActiveEffects.isEmpty()) {
            return;
        }
        List<EffectInstance> layers = ActiveEffects.getSnapshot();
        long now = System.nanoTime();
        boolean rendered = false;
        for (EffectInstance fx : layers) {
            if (fx.isOverHud() != overHud || (config.reduceFlashing && fx.isFlashing())) {
                continue;
            }
            float intensity = fx.getEnvelope(now) * fx.getIntensity() * config.maxIntensity;
            if (intensity <= MIN_VISIBLE_INTENSITY || (fx.isCustom() && !config.allowPackEffects)) {
                continue;
            }
            String key = fx.getPipeline().toString();
            if (RUNTIME_FAILED.contains(key) || !EffectValidator.validate(client, fx, key)) {
                continue;
            }
            rendered |= renderOne(client, fx, key, intensity, now);
        }
        if (rendered) {
            POOL.decrementLifespan();
        }
    }

    private static boolean renderOne(MinecraftClient client, EffectInstance fx, String key, float intensity,
                                     long now) {
        try {
            PostEffectProcessor processor = client.getShaderLoader()
                    .loadPostEffect(fx.getPipeline(), DefaultFramebufferSet.MAIN_ONLY);
            if (processor == null) {
                RUNTIME_FAILED.add(key);
                EffectFailures.fail(fx.getEffectId(), "post chain failed to load");
                return false;
            }
            UniformWriter.write(processor, fx, intensity, now);
            processor.render(client.getFramebuffer(), POOL);
            return true;
        } catch (RuntimeException ex) {
            RUNTIME_FAILED.add(key);
            EffectFailures.fail(fx.getEffectId(), "render error: " + ex);
            return false;
        }
    }
}
