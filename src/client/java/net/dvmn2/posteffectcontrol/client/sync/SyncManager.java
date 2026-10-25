package net.dvmn2.posteffectcontrol.client.sync;

import net.dvmn2.posteffectcontrol.client.PostEffectControlConfig;
import net.dvmn2.posteffectcontrol.client.network.CapsPayload;
import net.dvmn2.posteffectcontrol.client.network.PostEffectControlProtocol;
import net.dvmn2.posteffectcontrol.client.network.StatusPayload;
import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.dvmn2.posteffectcontrol.client.state.ActiveEffects;
import net.dvmn2.posteffectcontrol.client.state.EffectFailures;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.CustomPayload;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Receives the layer set from the plugin, reports the available pack effects (caps) and the status to the
 * server. The mod itself downloads nothing and reloads no resources: effects come from the player's resource
 * packs (installed by hand or delivered by ResourceControl). The effect list is sent again after every
 * resource reload. Client thread only.
 */
public final class SyncManager {

    private static final int STATUS_PERIOD_TICKS = 20;
    private static final int MAX_STATUS_MESSAGE_CHARS = 200;
    private static final int CAPS_BASE_BYTES = 16;
    private static final int CAPS_EFFECT_BASE_BYTES = 48;
    private static final int CAPS_PARAM_BYTES = 48;

    private boolean capsDirty = true;
    private int seenRevision = -1;
    private String lastCapsSignature = "";
    private String lastStatusSignature = "";
    private int ticks;

    /**
     * Called after joining a server: the server knows nothing about our list yet, so it is sent again as soon
     * as the channel is available.
     */
    public void onJoin(MinecraftClient client) {
        capsDirty = true;
        lastCapsSignature = "";
        lastStatusSignature = "";
    }

    /**
     * Called after leaving a server: forgets everything about the connection.
     */
    public void onDisconnect(MinecraftClient client) {
        ActiveEffects.clear();
        EffectFailures.clear();
        lastStatusSignature = "";
        lastCapsSignature = "";
        capsDirty = true;
    }

    /**
     * Applies the layer set from the server.
     */
    public void onSync(MinecraftClient client, SyncPayload payload) {
        if (payload.protocol() != PostEffectControlProtocol.PROTOCOL) {
            return;
        }
        ActiveEffects.apply(client, payload);
        refreshStatus(client, true);
    }

    /**
     * Called every client tick: sends the effect list when it changed and the status periodically.
     */
    public void onTick(MinecraftClient client) {
        int revision = PackEffects.getRevision();
        if (revision != seenRevision) {
            seenRevision = revision;
            capsDirty = true;
        }
        // While resources reload (loading overlay) the registry is incomplete: wait for the end.
        if (capsDirty && client.getOverlay() == null && client.getNetworkHandler() != null) {
            sendCaps(client);
        }
        if (++ticks % STATUS_PERIOD_TICKS == 0) {
            refreshStatus(client, false);
        }
    }

    /**
     * Called by commands after a player setting changed.
     */
    public void configChanged(MinecraftClient client) {
        capsDirty = true;
        refreshStatus(client, true);
    }

    private void sendCaps(MinecraftClient client) {
        if (!canSend(CapsPayload.ID)) {
            return; // a server without the plugin, or the channel list has not arrived yet: try next tick
        }
        List<CapsPayload.Effect> list = new ArrayList<>();
        if (PostEffectControlConfig.get().allowPackEffects) {
            int budget = CAPS_BASE_BYTES;
            for (PackEffects.Meta meta : PackEffects.getAll(client).values()) {
                int size = CAPS_EFFECT_BASE_BYTES + bytes(meta.id().toString()) + bytes(meta.displayName())
                        + meta.params().size() * CAPS_PARAM_BYTES;
                if (list.size() >= CapsPayload.MAX_EFFECTS || budget + size > CapsPayload.MAX_BYTES) {
                    break;
                }
                budget += size;
                list.add(toCapsEffect(meta));
            }
        }
        capsDirty = false;
        String signature = list.toString();
        if (signature.equals(lastCapsSignature)) {
            return;
        }
        lastCapsSignature = signature;
        send(new CapsPayload(PostEffectControlProtocol.PROTOCOL, List.copyOf(list)));
    }

    private static CapsPayload.Effect toCapsEffect(PackEffects.Meta meta) {
        List<CapsPayload.Param> params = new ArrayList<>();
        for (PackEffects.Param param : meta.params()) {
            params.add(new CapsPayload.Param(param.name(), param.index(), param.def(), param.min(), param.max()));
        }
        return new CapsPayload.Effect(meta.id().toString(), meta.displayName(), meta.flashing(), meta.overHud(),
                meta.defaultIntensity(), meta.defaultColor(), List.copyOf(params));
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private void refreshStatus(MinecraftClient client, boolean force) {
        if (client.getNetworkHandler() == null) {
            return;
        }
        PostEffectControlConfig config = PostEffectControlConfig.get();
        boolean iris = FabricLoader.getInstance().isModLoaded("iris");
        int active = ActiveEffects.getAssignedCount();

        StatusPayload.State state;
        String message;
        if (!EffectFailures.isEmpty()) {
            state = StatusPayload.State.FAILED;
            message = EffectFailures.getSummary();
        } else if (client.getOverlay() != null) {
            state = StatusPayload.State.WORKING;
            message = "reloading resources";
        } else {
            state = StatusPayload.State.READY;
            message = "";
        }
        if (message.length() > MAX_STATUS_MESSAGE_CHARS) {
            message = message.substring(0, MAX_STATUS_MESSAGE_CHARS);
        }

        String signature = state + "|" + message + "|" + iris + "|" + config.enabled + "|" + active;
        if (!force && signature.equals(lastStatusSignature)) {
            return;
        }
        lastStatusSignature = signature;
        send(new StatusPayload(PostEffectControlProtocol.PROTOCOL, state, message, iris, config.enabled, active));
    }

    private static boolean canSend(CustomPayload.Id<?> id) {
        try {
            return ClientPlayNetworking.canSend(id);
        } catch (RuntimeException ex) {
            return false; // no connection yet
        }
    }

    private static void send(CustomPayload payload) {
        try {
            ClientPlayNetworking.send(payload);
        } catch (RuntimeException ex) {
            // No connection or the channel is unavailable: status is informational, nothing to do.
        }
    }
}
