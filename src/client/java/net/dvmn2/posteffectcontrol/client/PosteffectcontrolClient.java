package net.dvmn2.posteffectcontrol.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.dvmn2.posteffectcontrol.client.effect.ActiveEffects;
import net.dvmn2.posteffectcontrol.client.network.CapsPayload;
import net.dvmn2.posteffectcontrol.client.network.StatusPayload;
import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.text.Text;

public final class PostEffectControlClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Регистрация приёмника заодно сообщает серверу (minecraft:register), что мод установлен —
        // по этому плагин решает, слать ли эффекты и кикать ли игрока.
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StatusPayload.ID, StatusPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CapsPayload.ID, CapsPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) ->
                context.client().execute(() -> EffectSync.onSync(context.client(), payload)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EffectSync.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EffectSync.onDisconnect(client));
        ClientTickEvents.END_CLIENT_TICK.register(EffectSync::onTick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("pec")
                        .executes(ctx -> status(ctx.getSource()))
                        .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                        // Аварийный выключатель: мгновенно прячет все эффекты (таймеры идут дальше).
                        .then(ClientCommandManager.literal("off").executes(ctx -> {
                            setEnabled(ctx.getSource(), false);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("on").executes(ctx -> {
                            setEnabled(ctx.getSource(), true);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("max")
                                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(ctx -> {
                                            ClientConfig cfg = ClientConfig.get();
                                            cfg.maxIntensity = FloatArgumentType.getFloat(ctx, "value");
                                            cfg.save();
                                            ctx.getSource().sendFeedback(Text.translatable("posteffectcontrol.max", cfg.maxIntensity));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("noflash")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ClientConfig cfg = ClientConfig.get();
                                            cfg.reduceFlashing = BoolArgumentType.getBool(ctx, "value");
                                            cfg.save();
                                            ctx.getSource().sendFeedback(Text.translatable(
                                                    cfg.reduceFlashing ? "posteffectcontrol.noflash.on" : "posteffectcontrol.noflash.off"));
                                            return 1;
                                        })))
                        // Разрешить серверу использовать эффекты из ресурспаков игрока.
                        .then(ClientCommandManager.literal("packeffects")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            ClientConfig cfg = ClientConfig.get();
                                            cfg.allowPackEffects = BoolArgumentType.getBool(ctx, "value");
                                            cfg.save();
                                            EffectSync.configChanged(ctx.getSource().getClient());
                                            ctx.getSource().sendFeedback(Text.translatable(
                                                    cfg.allowPackEffects ? "posteffectcontrol.packeffects.on" : "posteffectcontrol.packeffects.off"));
                                            return 1;
                                        })))));
    }

    private static void setEnabled(FabricClientCommandSource source, boolean enabled) {
        ClientConfig cfg = ClientConfig.get();
        cfg.enabled = enabled;
        cfg.save();
        EffectSync.configChanged(source.getClient());
        source.sendFeedback(Text.translatable(enabled ? "posteffectcontrol.enabled" : "posteffectcontrol.disabled"));
    }

    private static int status(FabricClientCommandSource source) {
        ClientConfig cfg = ClientConfig.get();
        source.sendFeedback(Text.translatable("posteffectcontrol.status",
                cfg.enabled, cfg.maxIntensity, cfg.reduceFlashing, cfg.allowPackEffects, ActiveEffects.assignedCount()));
        return 1;
    }
}
