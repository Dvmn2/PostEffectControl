package net.dvmn2.posteffectcontrol.client.mixin;

import net.dvmn2.posteffectcontrol.client.render.EffectRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two render points: right after the world and before the HUD (like the vanilla post processor) for effects
 * under the HUD, and after the GUI for effects with the over-HUD flag.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V",
                    shift = At.Shift.AFTER))
    private void posteffectcontrol$afterWorld(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        EffectRenderer.render(MinecraftClient.getInstance(), false);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;"
                            + "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    shift = At.Shift.AFTER))
    private void posteffectcontrol$afterGui(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        EffectRenderer.render(MinecraftClient.getInstance(), true);
    }
}
