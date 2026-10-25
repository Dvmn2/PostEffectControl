package net.dvmn2.posteffectcontrol.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Access to the uniform buffers of a post effect pass.
 */
@Mixin(PostEffectPass.class)
public interface PostEffectPassAccessor {

    @Accessor("uniformBuffers")
    Map<String, GpuBuffer> posteffectcontrol$getUniformBuffers();
}
