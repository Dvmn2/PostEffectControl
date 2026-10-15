package net.dvmn2.posteffectcontrol.client.shader;

import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Один проход постэффекта: вершинный и фрагментный шейдеры, имена входных семплеров
 * (без суффикса Sampler) и имена объявленных uniform-блоков. Нужен для проверки компиляции
 * до первого рендера (см. {@code EffectRenderer}).
 */
public record PassInfo(Identifier vertex, Identifier fragment, List<String> samplers, List<String> uniformBlocks) {
}
