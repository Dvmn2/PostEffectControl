package net.dvmn2.posteffectcontrol.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.dvmn2.posteffectcontrol.client.mixin.PostEffectPassAccessor;
import net.dvmn2.posteffectcontrol.client.mixin.PostEffectProcessorAccessor;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.dvmn2.posteffectcontrol.client.state.EffectInstance;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import org.lwjgl.system.MemoryStack;

import java.util.List;
import java.util.Map;

/**
 * Replaces the EffectParams uniform block (intensity, time, color, parameters) before every frame. The vanilla
 * {@link PostEffectProcessor} creates its uniform buffers once from the static JSON, so they must be swapped.
 */
final class UniformWriter {

    private static final String PARAMS_BLOCK = PackEffects.PARAMS_BLOCK;
    private static final int BLOCK_BYTES = 64;
    private static final double NANOS_PER_SECOND = 1.0e9;
    private static final double TIME_WRAP_SECONDS = 3600.0;
    private static final float COLOR_MAX = 255f;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;
    private static final int BYTE_MASK = 0xFF;

    private UniformWriter() {
    }

    /**
     * Writes the block into every pass of the processor that declares it.
     */
    static void write(PostEffectProcessor processor, EffectInstance fx, float intensity, long now) {
        float time = (float) (((now - fx.getStartNanos()) / NANOS_PER_SECOND) % TIME_WRAP_SECONDS);
        int color = fx.getColor();
        float r = ((color >> RED_SHIFT) & BYTE_MASK) / COLOR_MAX;
        float g = ((color >> GREEN_SHIFT) & BYTE_MASK) / COLOR_MAX;
        float b = (color & BYTE_MASK) / COLOR_MAX;
        float[] p = fx.getParams();

        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) (Object) processor)
                .posteffectcontrol$getPasses();
        for (PostEffectPass pass : passes) {
            Map<String, GpuBuffer> buffers = ((PostEffectPassAccessor) (Object) pass)
                    .posteffectcontrol$getUniformBuffers();
            if (!buffers.containsKey(PARAMS_BLOCK)) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, BLOCK_BYTES);
                builder.putVec4(intensity, time, 0f, fx.getSeed());
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
}
