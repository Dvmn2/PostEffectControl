package net.dvmn2.posteffectcontrol.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки игрока: сервер никогда не может их обойти. Файл config/posteffectcontrol.json.
 */
public final class ClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance;

    /** Показывать эффекты вообще. */
    public boolean enabled = true;
    /** Потолок интенсивности любого эффекта, 0..1. */
    public float maxIntensity = 1.0f;
    /** Не показывать эффекты, помеченные в ресурспаке как мигающие (риск для людей с фоточувствительной эпилепсией). */
    public boolean reduceFlashing = false;
    /** Разрешать серверу использовать эффекты из ресурспаков игрока (сообщать им список и рисовать их). */
    public boolean allowPackEffects = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("posteffectcontrol.json");
    }

    public static synchronized ClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ClientConfig load() {
        Path file = file();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
                if (loaded != null) {
                    loaded.maxIntensity = Math.max(0f, Math.min(1f, loaded.maxIntensity));
                    return loaded;
                }
            } catch (Exception ex) {
                System.err.println("[PostEffectControl] Не удалось прочитать конфиг: " + ex.getMessage());
            }
        }
        return new ClientConfig();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ex) {
            System.err.println("[PostEffectControl] Не удалось сохранить конфиг: " + ex.getMessage());
        }
    }
}
