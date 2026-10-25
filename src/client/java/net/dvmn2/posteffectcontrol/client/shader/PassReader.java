package net.dvmn2.posteffectcontrol.client.shader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the passes of an effect from its post_effect JSON.
 */
public final class PassReader {

    private static final Logger LOGGER = LoggerFactory.getLogger("posteffectcontrol");

    private PassReader() {
    }

    /**
     * Reads the passes of a pack effect. An empty list means the file is missing or could not be parsed.
     */
    public static List<PassInfo> read(MinecraftClient client, Identifier effectId) {
        Identifier file = Identifier.of(effectId.getNamespace(), "post_effect/" + effectId.getPath() + ".json");
        Optional<Resource> resource = client.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return List.of();
        }
        try (Reader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<PassInfo> list = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("passes")) {
                PassInfo pass = readPass(element.getAsJsonObject());
                if (pass == null) {
                    return List.of();
                }
                list.add(pass);
            }
            return list;
        } catch (IOException | RuntimeException ex) {
            // A broken file in a resource pack must not break the game; the effect is reported as failed.
            LOGGER.warn("Could not read {}: {}", file, ex.getMessage());
            return List.of();
        }
    }

    private static @Nullable PassInfo readPass(JsonObject pass) {
        Identifier vertex = Identifier.tryParse(pass.get("vertex_shader").getAsString());
        Identifier fragment = Identifier.tryParse(pass.get("fragment_shader").getAsString());
        if (vertex == null || fragment == null) {
            return null;
        }
        List<String> samplers = new ArrayList<>();
        if (pass.has("inputs")) {
            JsonArray inputs = pass.getAsJsonArray("inputs");
            for (JsonElement input : inputs) {
                samplers.add(input.getAsJsonObject().get("sampler_name").getAsString());
            }
        }
        List<String> blocks = new ArrayList<>();
        if (pass.has("uniforms") && pass.get("uniforms").isJsonObject()) {
            blocks.addAll(pass.getAsJsonObject("uniforms").keySet());
        }
        return new PassInfo(vertex, fragment, List.copyOf(samplers), List.copyOf(blocks));
    }
}
