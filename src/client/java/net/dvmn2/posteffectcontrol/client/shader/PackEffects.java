package net.dvmn2.posteffectcontrol.client.shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Эффекты, лежащие в ресурспаках клиента (установленных вручную или доставленных через ResourceControl).
 * <p>
 * Эффект {@code ns:path} состоит из двух файлов ресурспака:
 * <ul>
 *   <li>{@code assets/<ns>/post_effect/<path>.json} — обычный ванильный post_effect (шейдеры лежат в
 *       {@code assets/<ns>/shaders/...});</li>
 *   <li>{@code assets/<ns>/posteffectcontrol/<path>.json} — описание для мода: название, флаги
 *       {@code flashing}/{@code over_hud}, значения по умолчанию и имена параметров.</li>
 * </ul>
 * Реестр строится лениво и сбрасывается при каждой перезагрузке шейдеров ({@link #markDirty()}).
 * Флаги берутся из пака игрока, а не от сервера: сервер не может скрыть от игрока, что эффект мигает.
 */
public final class PackEffects {

    public static final String META_DIR = "posteffectcontrol";
    public static final String PARAMS_BLOCK = "EffectParams";
    /** Лимит эффектов в реестре (и в пакете pe_caps). */
    public static final int MAX_EFFECTS = 48;
    /** Идентификатор {@code ns:path} должен помещаться в сетевые строки и ключи слоёв. */
    public static final int MAX_ID_LENGTH = 80;
    public static final int MAX_PARAMS = 8;

    private static final Pattern PARAM_NAME = Pattern.compile("[a-z0-9_]{1,24}");

    public record Param(String name, int index, float def, float min, float max) {
    }

    public record Meta(Identifier id, String displayName, boolean flashing, boolean overHud,
                       float defaultIntensity, int defaultColor, List<Param> params) {

        /** Значение параметра pN по умолчанию (0, если параметр не описан). */
        public float defaultParam(int index) {
            for (Param p : params) {
                if (p.index() == index) {
                    return p.def();
                }
            }
            return 0f;
        }
    }

    private static final Map<String, Meta> REGISTRY = new LinkedHashMap<>();
    private static boolean dirty = true;

    private PackEffects() {
    }

    /** Ресурсы изменились — реестр нужно перечитать при следующем обращении. */
    public static synchronized void markDirty() {
        dirty = true;
    }

    public static synchronized Map<String, Meta> all(MinecraftClient client) {
        rebuildIfNeeded(client);
        return new LinkedHashMap<>(REGISTRY);
    }

    public static synchronized Meta get(MinecraftClient client, String id) {
        rebuildIfNeeded(client);
        return REGISTRY.get(id);
    }

    // ------------------------------------------------------------------
    // Построение реестра
    // ------------------------------------------------------------------

    private static void rebuildIfNeeded(MinecraftClient client) {
        if (!dirty) {
            return;
        }
        ResourceManager manager = client.getResourceManager();
        if (manager == null) {
            return;
        }
        dirty = false;
        REGISTRY.clear();

        Map<Identifier, Resource> found;
        try {
            found = manager.findResources(META_DIR, id -> id.getPath().endsWith(".json"));
        } catch (RuntimeException ex) {
            System.err.println("[PostEffectControl] Не удалось просмотреть ресурсы: " + ex);
            return;
        }
        List<Identifier> files = new ArrayList<>(found.keySet());
        files.sort(Comparator.comparing(Identifier::toString));

        for (Identifier file : files) {
            String path = file.getPath();
            int from = META_DIR.length() + 1;
            int to = path.length() - ".json".length();
            if (!path.startsWith(META_DIR + "/") || to <= from) {
                continue;
            }
            String ns = file.getNamespace();
            if (ns.equals("minecraft")) {
                continue; // короткие имена без namespace зарезервированы за встроенными эффектами
            }
            String rel = path.substring(from, to);
            Identifier id = Identifier.of(ns, rel);
            if (id.toString().length() > MAX_ID_LENGTH) {
                System.err.println("[PostEffectControl] " + id + ": идентификатор длиннее " + MAX_ID_LENGTH + " символов, пропущен");
                continue;
            }
            if (manager.getResource(Identifier.of(ns, "post_effect/" + rel + ".json")).isEmpty()) {
                System.err.println("[PostEffectControl] " + id + ": нет файла post_effect/" + rel + ".json, эффект пропущен");
                continue;
            }
            if (REGISTRY.size() >= MAX_EFFECTS) {
                System.err.println("[PostEffectControl] Найдено больше " + MAX_EFFECTS + " эффектов, остальные пропущены");
                break;
            }
            try (Reader reader = new InputStreamReader(found.get(file).getInputStream(), StandardCharsets.UTF_8)) {
                REGISTRY.put(id.toString(), parseMeta(id, JsonParser.parseReader(reader)));
            } catch (Exception ex) {
                System.err.println("[PostEffectControl] " + file + ": " + ex);
            }
        }
    }

    private static Meta parseMeta(Identifier id, JsonElement element) {
        JsonObject o = element.getAsJsonObject();
        String name = o.has("display_name") ? o.get("display_name").getAsString() : id.toString();
        name = sanitize(name, 48);
        boolean flashing = o.has("flashing") && o.get("flashing").getAsBoolean();
        boolean overHud = o.has("over_hud") && o.get("over_hud").getAsBoolean();
        float intensity = o.has("default_intensity") ? clamp01(o.get("default_intensity").getAsFloat()) : 1f;
        int color = o.has("default_color") ? parseColor(o.get("default_color")) : 0xFFFFFF;

        List<Param> params = new ArrayList<>();
        if (o.has("params") && o.get("params").isJsonObject()) {
            Set<Integer> usedIndexes = new HashSet<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("params").entrySet()) {
                String pname = e.getKey().toLowerCase(Locale.ROOT);
                if (!PARAM_NAME.matcher(pname).matches() || !e.getValue().isJsonObject() || params.size() >= MAX_PARAMS) {
                    continue;
                }
                JsonObject p = e.getValue().getAsJsonObject();
                int index = p.has("index") ? p.get("index").getAsInt() : params.size();
                if (index < 0 || index >= MAX_PARAMS || !usedIndexes.add(index)) {
                    continue;
                }
                float def = finite(p, "default", 0f);
                float min = finite(p, "min", 0f);
                float max = finite(p, "max", 1f);
                params.add(new Param(pname, index, def, Math.min(min, max), Math.max(min, max)));
            }
        }
        return new Meta(id, name, flashing, overHud, intensity, color, List.copyOf(params));
    }

    private static float finite(JsonObject o, String field, float fallback) {
        if (!o.has(field)) {
            return fallback;
        }
        float v = o.get(field).getAsFloat();
        return Float.isFinite(v) ? v : fallback;
    }

    private static int parseColor(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsInt() & 0xFFFFFF;
        }
        String s = el.getAsString().trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        return Integer.parseInt(s, 16) & 0xFFFFFF;
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 1f;
    }

    private static String sanitize(String s, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < max; i++) {
            char c = s.charAt(i);
            sb.append(c < 0x20 ? ' ' : c);
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------
    // Проходы для проверки компиляции
    // ------------------------------------------------------------------

    /**
     * Читает проходы из post_effect JSON эффекта. Пустой список — файла нет или он не разобрался.
     */
    public static List<PassInfo> readPasses(MinecraftClient client, Identifier effectId) {
        Identifier file = Identifier.of(effectId.getNamespace(), "post_effect/" + effectId.getPath() + ".json");
        Optional<Resource> resource = client.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return List.of();
        }
        try (Reader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<PassInfo> list = new ArrayList<>();
            for (JsonElement el : root.getAsJsonArray("passes")) {
                JsonObject p = el.getAsJsonObject();
                Identifier vertex = Identifier.tryParse(p.get("vertex_shader").getAsString());
                Identifier fragment = Identifier.tryParse(p.get("fragment_shader").getAsString());
                if (vertex == null || fragment == null) {
                    return List.of();
                }
                List<String> samplers = new ArrayList<>();
                if (p.has("inputs")) {
                    JsonArray inputs = p.getAsJsonArray("inputs");
                    for (JsonElement in : inputs) {
                        samplers.add(in.getAsJsonObject().get("sampler_name").getAsString());
                    }
                }
                List<String> blocks = new ArrayList<>();
                if (p.has("uniforms") && p.get("uniforms").isJsonObject()) {
                    blocks.addAll(p.getAsJsonObject("uniforms").keySet());
                }
                list.add(new PassInfo(vertex, fragment, List.copyOf(samplers), List.copyOf(blocks)));
            }
            return list;
        } catch (Exception ex) {
            System.err.println("[PostEffectControl] Не удалось прочитать " + file + ": " + ex.getMessage());
            return List.of();
        }
    }
}
