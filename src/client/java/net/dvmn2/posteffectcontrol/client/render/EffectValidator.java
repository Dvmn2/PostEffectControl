package net.dvmn2.posteffectcontrol.client.render;

import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.dvmn2.posteffectcontrol.client.PostEffectControlClient;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.dvmn2.posteffectcontrol.client.shader.PassInfo;
import net.dvmn2.posteffectcontrol.client.shader.PassReader;
import net.dvmn2.posteffectcontrol.client.state.EffectFailures;
import net.dvmn2.posteffectcontrol.client.state.EffectInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks an effect before its first use: the post chain exists and its shaders compile. This matters because
 * an error while loading a post chain in the vanilla shader loader resets the player's resource packs.
 * Results are cached until the next shader reload. Client thread only.
 */
final class EffectValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger("posteffectcontrol");
    private static final Identifier QUAD = Identifier.of(PostEffectControlClient.MOD_ID, "post/quad");
    private static final Identifier BLIT = Identifier.of(PostEffectControlClient.MOD_ID, "post/blit");
    private static final String SAMPLER_INFO = "SamplerInfo";

    /**
     * Pipeline id to whether it passed the check.
     */
    private static final Map<String, Boolean> VALID = new HashMap<>();

    private EffectValidator() {
    }

    /**
     * Forgets cached results (after a shader reload).
     */
    static void reset() {
        VALID.clear();
    }

    /**
     * Returns whether the effect may be drawn; a failure is reported once.
     */
    static boolean validate(MinecraftClient client, EffectInstance fx, String key) {
        Boolean cached = VALID.get(key);
        if (cached != null) {
            return cached;
        }
        List<PassInfo> passes = fx.isCustom()
                ? PassReader.read(client, fx.getPipeline()) : builtinPasses(fx.getEffectId());
        boolean ok = !passes.isEmpty();
        String reason = passes.isEmpty()
                ? "post_effect json is missing or invalid" : "shader failed to compile (see client log)";
        for (PassInfo pass : passes) {
            if (!compiles(client, pass)) {
                ok = false;
                break;
            }
        }
        VALID.put(key, ok);
        if (!ok) {
            EffectFailures.fail(fx.getEffectId(), reason);
        }
        return ok;
    }

    private static List<PassInfo> builtinPasses(String id) {
        return List.of(
                new PassInfo(QUAD, Identifier.of(PostEffectControlClient.MOD_ID, "post/fx/" + id),
                        List.of("In"), List.of(PackEffects.PARAMS_BLOCK)),
                new PassInfo(QUAD, BLIT, List.of("In"), List.of()));
    }

    /**
     * Builds the same pipeline as the vanilla parser and tries to compile it.
     */
    private static boolean compiles(MinecraftClient client, PassInfo pass) {
        try {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
                    .withVertexShader(pass.vertex())
                    .withFragmentShader(pass.fragment())
                    .withLocation(pass.fragment().withPrefixedPath("validate/"));
            for (String sampler : pass.samplers()) {
                builder.withSampler(sampler + "Sampler");
            }
            builder.withUniform(SAMPLER_INFO, UniformType.UNIFORM_BUFFER);
            for (String block : pass.uniformBlocks()) {
                if (!block.equals(SAMPLER_INFO)) {
                    builder.withUniform(block, UniformType.UNIFORM_BUFFER);
                }
            }
            CompiledRenderPipeline compiled = RenderSystem.getDevice()
                    .precompilePipeline(builder.build(), client.getShaderLoader()::getSource);
            return compiled.isValid();
        } catch (RuntimeException ex) {
            LOGGER.warn("Shader check of {} failed", pass.fragment(), ex);
            return false;
        }
    }
}
