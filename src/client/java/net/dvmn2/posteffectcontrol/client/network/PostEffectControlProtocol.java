package net.dvmn2.posteffectcontrol.client.network;

/**
 * Protocol constants shared by all PostEffectControl payloads. {@link #PROTOCOL} must equal the plugin's
 * {@code SyncManager.PROTOCOL}.
 */
public final class PostEffectControlProtocol {

    /**
     * Protocol version; increased on every change of a packet format.
     */
    public static final int PROTOCOL = 4;

    /**
     * Protocol value of a packet that could not be decoded; receivers ignore it.
     */
    public static final int INVALID_PROTOCOL = -1;

    private PostEffectControlProtocol() {
    }
}
