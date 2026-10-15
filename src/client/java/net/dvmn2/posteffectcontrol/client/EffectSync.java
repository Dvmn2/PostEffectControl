package net.dvmn2.posteffectcontrol.client;

import net.dvmn2.posteffectcontrol.client.effect.ActiveEffects;
import net.dvmn2.posteffectcontrol.client.network.CapsPayload;
import net.dvmn2.posteffectcontrol.client.network.StatusPayload;
import net.dvmn2.posteffectcontrol.client.network.SyncPayload;
import net.dvmn2.posteffectcontrol.client.shader.PackEffects;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Связующее звено: принимает pe_sync, сообщает серверу список доступных эффектов (pe_caps) и статус (pe_status).
 * <p>
 * Мод сам ничего не скачивает и не перезагружает ресурсы: эффекты берутся из ресурспаков клиента
 * (установленных вручную или доставленных ResourceControl). После каждой перезагрузки ресурсов
 * список эффектов отправляется серверу заново. Только клиентский поток.
 */
public final class EffectSync {

    public static final int PROTOCOL = 3;

    /** effectId -> текст ошибки. */
    private static final Map<String, String> failures = new LinkedHashMap<>();

    private static boolean capsDirty = true;
    private static String lastCapsSignature = "";
    private static String lastStatusSignature = "";
    private static int ticks = 0;

    private EffectSync() {
    }

    // ------------------------------------------------------------------
    // Жизненный цикл
    // ------------------------------------------------------------------

    public static void onJoin(MinecraftClient client) {
        // Сервер ничего не знает о нашем списке — отправим заново, как только канал станет доступен.
        capsDirty = true;
        lastCapsSignature = "";
        lastStatusSignature = "";
    }

    public static void onDisconnect(MinecraftClient client) {
        ActiveEffects.clear();
        failures.clear();
        lastStatusSignature = "";
        lastCapsSignature = "";
        capsDirty = true;
    }

    /** После каждой перезагрузки шейдеров (ShaderLoaderMixin): ресурсы изменились, старые ошибки неактуальны. */
    public static void onShadersReloaded() {
        failures.clear();
        PackEffects.markDirty();
        capsDirty = true;
    }

    // ------------------------------------------------------------------
    // Приём от плагина
    // ------------------------------------------------------------------

    public static void onSync(MinecraftClient client, SyncPayload payload) {
        if (payload.protocol() != PROTOCOL) {
            return;
        }
        ActiveEffects.onSync(client, payload);
        refreshStatus(client, true);
    }

    // ------------------------------------------------------------------
    // Тик: список эффектов и периодический статус
    // ------------------------------------------------------------------

    public static void onTick(MinecraftClient client) {
        // Во время перезагрузки ресурсов (оверлей загрузки) реестр ещё неполный — ждём её конца.
        if (capsDirty && client.getOverlay() == null && client.getNetworkHandler() != null) {
            sendCaps(client);
        }
        if (++ticks % 20 == 0) {
            refreshStatus(client, false);
        }
    }

    private static boolean canSendCaps() {
        try {
            return ClientPlayNetworking.canSend(CapsPayload.ID);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static void sendCaps(MinecraftClient client) {
        if (!canSendCaps()) {
            return; // сервер без плагина или список каналов ещё не пришёл — попробуем на следующем тике
        }
        List<CapsPayload.Effect> list = new ArrayList<>();
        if (ClientConfig.get().allowPackEffects) {
            int budget = 16;
            for (PackEffects.Meta m : PackEffects.all(client).values()) {
                int size = 48 + bytes(m.id().toString()) + bytes(m.displayName()) + m.params().size() * 48;
                if (list.size() >= CapsPayload.MAX_EFFECTS || budget + size > CapsPayload.MAX_BYTES) {
                    break;
                }
                budget += size;
                List<CapsPayload.Param> params = new ArrayList<>();
                for (PackEffects.Param p : m.params()) {
                    params.add(new CapsPayload.Param(p.name(), p.index(), p.def(), p.min(), p.max()));
                }
                list.add(new CapsPayload.Effect(m.id().toString(), m.displayName(), m.flashing(), m.overHud(),
                        m.defaultIntensity(), m.defaultColor(), List.copyOf(params)));
            }
        }
        capsDirty = false;
        String signature = list.toString();
        if (signature.equals(lastCapsSignature)) {
            return;
        }
        lastCapsSignature = signature;
        send(new CapsPayload(PROTOCOL, List.copyOf(list)));
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    // ------------------------------------------------------------------
    // Ошибки и статус
    // ------------------------------------------------------------------

    public static void fail(String effectId, String reason) {
        if (failures.put(effectId, effectId + ": " + reason) == null) {
            System.err.println("[PostEffectControl] " + effectId + ": " + reason);
        }
    }

    public static void refreshStatus(MinecraftClient client, boolean force) {
        if (client.getNetworkHandler() == null) {
            return;
        }
        ClientConfig cfg = ClientConfig.get();
        boolean iris = FabricLoader.getInstance().isModLoaded("iris");
        int active = ActiveEffects.assignedCount();

        int state;
        String message;
        if (!failures.isEmpty()) {
            state = StatusPayload.STATE_FAILED;
            message = String.join("; ", failures.values());
        } else if (client.getOverlay() != null) {
            state = StatusPayload.STATE_WORKING;
            message = "reloading resources";
        } else {
            state = StatusPayload.STATE_READY;
            message = "";
        }
        if (message.length() > 400) {
            message = message.substring(0, 400);
        }

        String signature = state + "|" + message + "|" + iris + "|" + cfg.enabled + "|" + active;
        if (!force && signature.equals(lastStatusSignature)) {
            return;
        }
        lastStatusSignature = signature;
        send(new StatusPayload(PROTOCOL, state, message, iris, cfg.enabled, active));
    }

    private static void send(net.minecraft.network.packet.CustomPayload payload) {
        try {
            ClientPlayNetworking.send(payload);
        } catch (RuntimeException ex) {
            // нет соединения или канал недоступен — не критично
        }
    }

    /** Нужен командам, чтобы оповестить о смене настроек. */
    public static void configChanged(MinecraftClient client) {
        capsDirty = true;
        refreshStatus(client, true);
    }

    public static boolean isRenderingBlocked() {
        return !ClientConfig.get().enabled;
    }
}
