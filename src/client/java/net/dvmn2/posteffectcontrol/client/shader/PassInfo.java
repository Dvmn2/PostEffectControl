package net.dvmn2.posteffectcontrol.client.shader;

import net.minecraft.util.Identifier;

import java.util.List;

/**
 * One pass of a post effect: vertex and fragment shaders, input sampler names (without the Sampler suffix)
 * and the declared uniform block names. Used to check compilation before the first render.
 */
public record PassInfo(Identifier vertex, Identifier fragment, List<String> samplers, List<String> uniformBlocks) {
}
