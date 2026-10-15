Example resource pack with one PostEffectControl effect: example:tint

Files:
  assets/example/post_effect/tint.json        vanilla post_effect (passes, uniforms)
  assets/example/shaders/post/tint.fsh        fragment shader
  assets/example/posteffectcontrol/tint.json  description for the mod (name, flags, defaults, parameter names)

Use:
  /posteffect give <player> example:tint 30s strength=0.8 color=#ff8800

Deliver via ResourceControl (/resourcecontrol pack add ... ; the plugin must have settings.allow-shaders: true)
or install the zip into the client's resourcepacks folder.
pack.mcmeta min_format/max_format: adjust to the format of your client version if the pack is shown as incompatible.
