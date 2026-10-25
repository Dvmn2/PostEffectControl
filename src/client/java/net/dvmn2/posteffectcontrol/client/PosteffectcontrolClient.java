package net.dvmn2.posteffectcontrol.client;

import net.dvmn2.posteffectcontrol.client.command.PostEffectControlCommand;
import net.dvmn2.posteffectcontrol.client.network.CapsPayload;
import net.dvmn2.posteffectcontrol.client.network.StatusPayload;
import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.dvmn2.posteffectcontrol.client.sync.SyncManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Client entry point: registers payloads, receivers, events and the command.
 */
public final class PostEffectControlClient implements ClientModInitializer {

    public static final String MOD_ID = "posteffectcontrol";

    @Override
    public void onInitializeClient() {
        SyncManager sync = new SyncManager();

        // Registering a receiver also tells the server (minecraft:register) that the mod is installed;
        // the plugin uses it to decide whether to send effects and whether to kick the player.
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StatusPayload.ID, StatusPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CapsPayload.ID, CapsPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) ->
                context.client().execute(() -> sync.onSync(context.client(), payload)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sync.onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> sync.onDisconnect(client));
        ClientTickEvents.END_CLIENT_TICK.register(sync::onTick);

        PostEffectControlCommand command = new PostEffectControlCommand(sync);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                command.register(dispatcher));
    }
}
