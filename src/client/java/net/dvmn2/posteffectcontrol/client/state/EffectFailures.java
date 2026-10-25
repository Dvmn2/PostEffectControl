package net.dvmn2.posteffectcontrol.client.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Errors of effects, reported to the server in the status message. Cleared after every shader reload
 * because the resources changed. Client thread only.
 */
public final class EffectFailures {

    private static final Logger LOGGER = LoggerFactory.getLogger("posteffectcontrol");
    private static final Map<String, String> FAILURES = new LinkedHashMap<>();

    private EffectFailures() {
    }

    /**
     * Records a failure; it is written to the log only the first time per effect.
     */
    public static void fail(String effectId, String reason) {
        if (FAILURES.put(effectId, effectId + ": " + reason) == null) {
            LOGGER.warn("{}: {}", effectId, reason);
        }
    }

    public static boolean isEmpty() {
        return FAILURES.isEmpty();
    }

    /**
     * Returns all failure texts joined into one line.
     */
    public static String getSummary() {
        return String.join("; ", FAILURES.values());
    }

    public static void clear() {
        FAILURES.clear();
    }
}
