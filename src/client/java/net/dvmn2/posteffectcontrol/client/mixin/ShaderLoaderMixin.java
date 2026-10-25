package net.dvmn2.posteffectcontrol.client.mixin;

import net.dvmn2.posteffectcontrol.client.render.EffectRenderer;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resets the cache of effect checks after every shader reload.
 */
@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderMixin {

    @Inject(method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;"
            + "Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("TAIL"))
    private void posteffectcontrol$onApply(ShaderLoader.Definitions definitions, ResourceManager resourceManager,
                                           Profiler profiler, CallbackInfo ci) {
        EffectRenderer.onShadersReloaded();
    }
}
