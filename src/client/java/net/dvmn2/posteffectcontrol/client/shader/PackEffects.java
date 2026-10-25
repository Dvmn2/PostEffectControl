package net.dvmn2.posteffectcontrol.client.shader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Effects that live in the player's resource packs (installed by hand or delivered by ResourceControl).
 * <p>
 * An effect {@code ns:path} consists of two files in the pack:
 * <ul>
 *   <li>{@code assets/<ns>/post_effect/<path>.json}: an ordinary vanilla post_effect (the shaders are in
 *       {@code assets/<ns>/shaders/...});</li>
 *   <li>{@code assets/<ns>/posteffectcontrol/<path>.json}: a description for the mod: name, the
 *       {@code flashing} and {@code over_hud} flags, defaults and parameter names.</li>
 * </ul>
 * The registry is built lazily and reset after every shader reload ({@link #markDirty()}). The flags come from
 * the player's pack, not from the server: a server cannot hide from the player that an effect flashes.
 */
public final class PackEffects {

    public static final String META_DIR = "posteffectcontrol";
    public static final String PARAMS_BLOCK = "EffectParams";

    /**
     * Limit of effects in the registry (and in the caps message).
     */
    public static final int MAX_EFFECTS = 48;

    /**
     * An id {@code ns:path} must fit into network strings and layer keys.
     */
    public static final int MAX_ID_LENGTH = 80;
    public static final int MAX_PARAMS = 8;

    private static final Logger LOGGER = LoggerFactory.getLogger("posteffectcontrol");
    private static final Pattern PARAM_NAME = Pattern.compile("[a-z0-9_]{1,24}");
    private static final int MAX_DISPLAY_NAME_CHARS = 48;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int HEX_RADIX = 16;
    private static final String JSON_SUFFIX = ".json";

    /**
     * A named parameter of an effect.
     */
    public record Param(String name, int index, float def, float min, float max) {
    }

    /**
     * Description of an effect from a resource pack.
     */
    public record Meta(Identifier id, String displayName, boolean flashing, boolean overHud,
                       float defaultIntensity, int defaultColor, List<Param> params) {

        /**
         * Returns the default of parameter pN, or 0 when the parameter is not described.
         */
        public float defaultParam(int index) {
            for (Param param : params) {
                if (param.index() == index) {
                    return param.def();
                }
            }
            return 0f;
        }
    }

    private static final Map<String, Meta> REGISTRY = new LinkedHashMap<>();
    private static boolean dirty = true;
    private static int revision;

    private PackEffects() {
    }

    /**
     * Resources changed: the registry is re-read on the next access.
     */
    public static synchronized void markDirty() {
        dirty = true;
        revision++;
    }

    /**
     * Returns a number that grows every time the resources changed.
     */
    public static synchronized int getRevision() {
        return revision;
    }

    /**
     * Returns every effect found in the resource packs.
     */
    public static synchronized Map<String, Meta> getAll(MinecraftClient client) {
        rebuildIfNeeded(client);
        return new LinkedHashMap<>(REGISTRY);
    }

    /**
     * Finds one effect, or null when no pack has it.
     */
    public static synchronized @Nullable Meta find(MinecraftClient client, String id) {
        rebuildIfNeeded(client);
        return REGISTRY.get(id);
    }

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
            found = manager.findResources(META_DIR, id -> id.getPath().endsWith(JSON_SUFFIX));
        } catch (RuntimeException ex) {
            LOGGER.warn("Could not scan resources for effects", ex);
            return;
        }
        List<Identifier> files = new ArrayList<>(found.keySet());
        files.sort(Comparator.comparing(Identifier::toString));

        for (Identifier file : files) {
            if (REGISTRY.size() >= MAX_EFFECTS) {
                LOGGER.warn("More than {} effects found, the rest are skipped", MAX_EFFECTS);
                break;
            }
            Meta meta = readEffect(manager, file, found.get(file));
            if (meta != null) {
                REGISTRY.put(meta.id().toString(), meta);
            }
        }
    }

    private static @Nullable Meta readEffect(ResourceManager manager, Identifier file, Resource resource) {
        String path = file.getPath();
        int from = META_DIR.length() + 1;
        int to = path.length() - JSON_SUFFIX.length();
        if (!path.startsWith(META_DIR + "/") || to <= from) {
            return null;
        }
        String namespace = file.getNamespace();
        if (namespace.equals("minecraft")) {
            return null; // short names without a namespace are reserved for built-in effects
        }
        String relative = path.substring(from, to);
        Identifier id = Identifier.of(namespace, relative);
        if (id.toString().length() > MAX_ID_LENGTH) {
            LOGGER.warn("{}: the id is longer than {} characters, skipped", id, MAX_ID_LENGTH);
            return null;
        }
        if (manager.getResource(Identifier.of(namespace, "post_effect/" + relative + JSON_SUFFIX)).isEmpty()) {
            LOGGER.warn("{}: post_effect/{}.json is missing, the effect is skipped", id, relative);
            return null;
        }
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return parseMeta(id, JsonParser.parseReader(reader));
        } catch (IOException | RuntimeException ex) {
            // A broken description in a resource pack only skips this effect.
            LOGGER.warn("{}: could not read the description", file, ex);
            return null;
        }
    }

    private static Meta parseMeta(Identifier id, JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        String name = object.has("display_name") ? object.get("display_name").getAsString() : id.toString();
        boolean flashing = object.has("flashing") && object.get("flashing").getAsBoolean();
        boolean overHud = object.has("over_hud") && object.get("over_hud").getAsBoolean();
        float intensity = object.has("default_intensity")
                ? clamp01(object.get("default_intensity").getAsFloat()) : 1f;
        int color = object.has("default_color") ? parseColor(object.get("default_color")) : RGB_MASK;

        List<Param> params = new ArrayList<>();
        if (object.has("params") && object.get("params").isJsonObject()) {
            Set<Integer> usedIndexes = new HashSet<>();
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("params").entrySet()) {
                Param param = parseParam(entry.getKey(), entry.getValue(), params.size(), usedIndexes);
                if (param != null) {
                    params.add(param);
                }
            }
        }
        return new Meta(id, sanitize(name), flashing, overHud, intensity, color, List.copyOf(params));
    }

    private static @Nullable Param parseParam(String rawName, JsonElement value, int position,
                                              Set<Integer> usedIndexes) {
        String name = rawName.toLowerCase(Locale.ROOT);
        if (!PARAM_NAME.matcher(name).matches() || !value.isJsonObject() || position >= MAX_PARAMS) {
            return null;
        }
        JsonObject object = value.getAsJsonObject();
        int index = object.has("index") ? object.get("index").getAsInt() : position;
        if (index < 0 || index >= MAX_PARAMS || !usedIndexes.add(index)) {
            return null;
        }
        float def = finite(object, "default", 0f);
        float min = finite(object, "min", 0f);
        float max = finite(object, "max", 1f);
        return new Param(name, index, def, Math.min(min, max), Math.max(min, max));
    }

    private static float finite(JsonObject object, String field, float fallback) {
        if (!object.has(field)) {
            return fallback;
        }
        float value = object.get(field).getAsFloat();
        return Float.isFinite(value) ? value : fallback;
    }

    private static int parseColor(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() & RGB_MASK;
        }
        String text = element.getAsString().trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        return Integer.parseInt(text, HEX_RADIX) & RGB_MASK;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 1f;
    }

    private static String sanitize(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length() && builder.length() < MAX_DISPLAY_NAME_CHARS; i++) {
            char c = text.charAt(i);
            builder.append(c < ' ' ? ' ' : c);
        }
        return builder.toString().trim();
    }
}
