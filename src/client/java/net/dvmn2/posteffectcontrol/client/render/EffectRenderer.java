package net.dvmn2.posteffectcontrol.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.dvmn2.posteffectcontrol.PostEffectControl;
import net.dvmn2.posteffectcontrol.client.ClientConfig;
import net.dvmn2.posteffectcontrol.client.EffectSync;
import net.dvmn2.posteffectcontrol.client.effect.ActiveEffects;
import net.dvmn2.posteffectcontrol.client.effect.EffectInstance;
import net.dvmn2.posteffectcontrol.client.mixin.PostEffectPassAccessor;
import net.dvmn2.posteffectcontrol.client.mixin.PostEffectProcessorAccessor;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.dvmn2.posteffectcontrol.client.shader.PassInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.util.memory.ObjectPool;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Рисует активные слои поверх мира (перед HUD) или поверх всего (флаг overHud).
 * <p>
 * Ванильный PostEffectProcessor создаёт uniform-буферы один раз из статического JSON, поэтому
 * блок EffectParams (интенсивность, время, цвет, параметры) подменяется перед каждым кадром.
 * Перед первым использованием эффект проверяется: компиляция шейдеров и наличие цепочки. Это важно:
 * ошибка загрузки цепочки в ванильном ShaderLoader сбрасывает ресурспаки игрока.
 * Эффекты из ресурспаков (идентификатор {@code ns:path}) грузятся тем же ванильным ShaderLoader, что и встроенные.
 */
public final class EffectRenderer {

    private static final Identifier QUAD = Identifier.of(PostEffectControl.MOD_ID, "post/quad");
    private static final Identifier BLIT = Identifier.of(PostEffectControl.MOD_ID, "post/blit");
    private static final String PARAMS_BLOCK = PackEffects.PARAMS_BLOCK;

    private static final ObjectPool POOL = new ObjectPool(3);
    /** pipelineId -> прошёл ли проверку. Сбрасывается при перезагрузке шейдеров. */
    private static final Map<String, Boolean> VALID = new HashMap<>();
    /** Эффекты, упавшие во время рендера — пропускаем до перезагрузки. */
    private static final Set<String> RUNTIME_FAILED = new HashSet<>();

    private EffectRenderer() {
    }

    public static void onShadersReloaded() {
        VALID.clear();
        RUNTIME_FAILED.clear();
        EffectSync.onShadersReloaded();
    }

    public static void render(MinecraftClient client, boolean overHud) {
        ClientConfig cfg = ClientConfig.get();
        if (!cfg.enabled || client.world == null || ActiveEffects.isEmpty()) {
            return;
        }
        List<EffectInstance> list = ActiveEffects.snapshot();
        long now = System.nanoTime();
        boolean rendered = false;
        for (EffectInstance fx : list) {
            if (fx.overHud != overHud || (cfg.reduceFlashing && fx.flashing)) {
                continue;
            }
            float k = fx.envelope(now) * fx.intensity * cfg.maxIntensity;
            if (k <= 0.002f) {
                continue;
            }
            if (fx.custom && !cfg.allowPackEffects) {
                continue;
            }
            String key = fx.pipeline.toString();
            if (RUNTIME_FAILED.contains(key)) {
                continue;
            }
            if (!validate(client, fx, key)) {
                continue;
            }
            try {
                PostEffectProcessor processor = client.getShaderLoader().loadPostEffect(fx.pipeline, DefaultFramebufferSet.MAIN_ONLY);
                if (processor == null) {
                    RUNTIME_FAILED.add(key);
                    EffectSync.fail(fx.effectId, "post chain failed to load");
                    continue;
                }
                writeParams(processor, fx, k, now);
                processor.render(client.getFramebuffer(), POOL);
                rendered = true;
            } catch (RuntimeException ex) {
                RUNTIME_FAILED.add(key);
                EffectSync.fail(fx.effectId, "render error: " + ex);
            }
        }
        if (rendered) {
            POOL.decrementLifespan();
        }
    }

    // ------------------------------------------------------------------
    // Uniform-блок
    // ------------------------------------------------------------------

    private static void writeParams(PostEffectProcessor processor, EffectInstance fx, float intensity, long now) {
        float time = (float) (((now - fx.startNanos) / 1.0e9) % 3600.0);
        float r = ((fx.color >> 16) & 0xFF) / 255f;
        float g = ((fx.color >> 8) & 0xFF) / 255f;
        float b = (fx.color & 0xFF) / 255f;
        float[] p = fx.params;

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) (Object) processor).posteffectcontrol$getPasses();
        for (PostEffectPass pass : passes) {
            Map<String, GpuBuffer> buffers = ((PostEffectPassAccessor) (Object) pass).posteffectcontrol$getUniformBuffers();
            if (!buffers.containsKey(PARAMS_BLOCK)) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, 64);
                builder.putVec4(intensity, time, 0f, fx.seed);
                builder.putVec4(r, g, b, 1f);
                builder.putVec4(p[0], p[1], p[2], p[3]);
                builder.putVec4(p[4], p[5], p[6], p[7]);
                GpuBuffer fresh = RenderSystem.getDevice().createBuffer(
                        () -> "PostEffectControl EffectParams", GpuBuffer.USAGE_UNIFORM, builder.get());
                GpuBuffer old = buffers.put(PARAMS_BLOCK, fresh);
                if (old != null) {
                    old.close();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Проверка перед первым использованием
    // ------------------------------------------------------------------

    private static boolean validate(MinecraftClient client, EffectInstance fx, String key) {
        Boolean cached = VALID.get(key);
        if (cached != null) {
            return cached;
        }
        List<PassInfo> passes = fx.custom ? PackEffects.readPasses(client, fx.pipeline) : builtinPasses(fx.effectId);
        boolean ok = !passes.isEmpty();
        String reason = passes.isEmpty() ? "post_effect json is missing or invalid" : "shader failed to compile (see client log)";
        for (PassInfo pass : passes) {
            if (!compiles(client, pass)) {
                ok = false;
                break;
            }
        }
        VALID.put(key, ok);
        if (!ok) {
            EffectSync.fail(fx.effectId, reason);
        }
        return ok;
    }

    private static List<PassInfo> builtinPasses(String id) {
        return List.of(
                new PassInfo(QUAD, Identifier.of(PostEffectControl.MOD_ID, "post/fx/" + id), List.of("In"), List.of(PARAMS_BLOCK)),
                new PassInfo(QUAD, BLIT, List.of("In"), List.of()));
    }

    /** Собирает такой же пайплайн, как ванильный парсер, и пробует скомпилировать. */
    private static boolean compiles(MinecraftClient client, PassInfo pass) {
        try {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                    .withVertexShader(pass.vertex())
                    .withFragmentShader(pass.fragment())
                    .withLocation(pass.fragment().withPrefixedPath("validate/"));
            for (String sampler : pass.samplers()) {
                builder.withSampler(sampler + "Sampler");
            }
            builder.withUniform("SamplerInfo", UniformType.UNIFORM_BUFFER);
            for (String block : pass.uniformBlocks()) {
                if (!block.equals("SamplerInfo")) {
                    builder.withUniform(block, UniformType.UNIFORM_BUFFER);
                }
            }
            CompiledRenderPipeline compiled = RenderSystem.getDevice()
                    .precompilePipeline(builder.build(), client.getShaderLoader()::getSource);
            return compiled.isValid();
        } catch (RuntimeException ex) {
            System.err.println("[PostEffectControl] Проверка шейдера " + pass.fragment() + " не удалась: " + ex);
            return false;
        }
    }
}
