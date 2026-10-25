# PostEffectControl

Fabric client mod that draws post-processing effects (built-in ones and effects from resource packs) sent by the server. Companion plugin: `PostEffectControlPlugin`.

## Requirements
- Minecraft 1.21.11, Java 21
- Fabric Loader >= 0.19.5, Fabric API (built against `0.141.6+1.21.11`)
- Companion plugin `PostEffectControlPlugin` >= 2.0.0 (protocol 4) on the server

## Build and run
```bash
./gradlew build      # build/libs/PostEffectControl-<version>.jar
./gradlew runClient  # Minecraft 1.21.11 dev client
```

## Architecture
Package `net.dvmn2.posteffectcontrol.client` (source set `client`; `src/main` holds only `fabric.mod.json`):

| Class | Responsibility |
|---|---|
| `PostEffectControlClient` | entry point: registers payloads, receivers, events and the command; holds `MOD_ID` |
| `PostEffectControlConfig` | player settings (`config/posteffectcontrol.json`); the server cannot override them |
| `command.PostEffectControlCommand` | `/posteffectcontrol` (alias `/pec`): status, off, on, max, noflash, packeffects |
| `mixin.GameRendererMixin` | two render points: after the world (before the HUD) and after the GUI (over-HUD effects) |
| `mixin.ShaderLoaderMixin` | calls `EffectRenderer.onShadersReloaded()` after every shader reload |
| `mixin.PostEffectProcessorAccessor`, `PostEffectPassAccessor` | access to the passes and uniform buffers of a vanilla post effect |
| `network.SyncPayload`, `StatusPayload`, `CapsPayload` | packets and their codecs (format in Javadoc) |
| `network.PostEffectControlProtocol` | `PROTOCOL` constant |
| `state.ActiveEffects`, `EffectInstance` | active layers, ordering by priority, fade envelope |
| `state.EffectFailures` | effect errors reported to the server |
| `shader.PackEffects` | registry of effects found in the player's resource packs |
| `shader.PassReader`, `PassInfo` | reads the passes of a post_effect JSON |
| `render.EffectRenderer` | draws the layers; guards the render hook |
| `render.EffectValidator` | checks that an effect's chain exists and its shaders compile |
| `render.UniformWriter` | replaces the `EffectParams` uniform block every frame |
| `sync.SyncManager` | applies server messages, sends the effect list (caps) and the status |
| `util.AtomicFiles` | atomic file writes |

Threading: everything runs on the client thread (messages are handed over with `client.execute`, rendering is on the same thread). The mod starts no threads of its own.

Lifecycle:
1. `onInitializeClient`: create `SyncManager`, register payload types, receivers, `JOIN`, `DISCONNECT`, tick event and the command.
2. On a `pe_sync` message: `ActiveEffects.apply`, then an immediate status.
3. Every tick: when resources changed (`PackEffects.getRevision()`) and no loading overlay is shown, the effect list is sent again; the status is refreshed once a second.
4. `DISCONNECT`: active layers and failures are cleared.

## Public API
None.

## Protocol
| Channel | Direction | Protocol | Format |
|---|---|---|---|
| `dvmn2:pe_sync` | S2C | 4 | `SyncPayload` Javadoc |
| `dvmn2:pe_status` | C2S | 4 | `StatusPayload` Javadoc |
| `dvmn2:pe_caps` | C2S | 4 | `CapsPayload` Javadoc |

Compatibility rules:
- The first field of every packet is a VarInt protocol version; `PostEffectControlProtocol.PROTOCOL` must equal the plugin's `SyncManager.PROTOCOL` and is increased on every format change.
- A packet with another protocol is ignored silently. Mod 2.x works only with plugin 2.x.
- A packet that fails to decode becomes a packet with `INVALID_PROTOCOL` (the rest of the buffer is skipped), so a malformed message never disconnects the player.
- Registering the `pe_sync` receiver makes the server see the mod as installed.

## Data and files
| File | Format | Version | Content |
|---|---|---|---|
| `config/posteffectcontrol.json` | JSON | none | `enabled` (default `true`), `maxIntensity` (`1.0`, range 0..1), `reduceFlashing` (`false`), `allowPackEffects` (`false`) |

The file is written atomically. Unknown or invalid values fall back to defaults (`maxIntensity` is clamped into 0..1). There is no migration from 1.x. The mod keeps no cache.

Built-in effects are mod resources: `assets/posteffectcontrol/post_effect/fx/<name>.json` and `assets/posteffectcontrol/shaders/post/fx/<name>.fsh` (shared `quad.vsh` and `blit.fsh`). Their descriptions must match the plugin's `EffectRegistry`.

Pack effect format (an effect `ns:path` is two files in a resource pack):
- `assets/<ns>/post_effect/<path>.json`: an ordinary vanilla post_effect chain (shaders in `assets/<ns>/shaders/...`);
- `assets/<ns>/posteffectcontrol/<path>.json`: `display_name`, `flashing`, `over_hud`, `default_intensity` (0..1), `default_color` (`#rrggbb` or a number) and `params` (name to `{ index, default, min, max }`, up to 8, names `[a-z0-9_]{1,24}`).

Shader contract: one uniform block `EffectParams` in std140 layout, four `vec4`: `(intensity, time, 0, seed)`, `(r, g, b, 1)`, `(p0..p3)`, `(p4..p7)`.

## Conventions and constraints
- The flashing and over-HUD flags of an effect come from the player's own resources (built-in: `glitch` is flashing). The server cannot hide that an effect flashes, and `reduceFlashing` hides all flashing effects.
- `maxIntensity` caps the strength of every layer, `enabled` stops drawing (timers keep running), and `allowPackEffects` is off by default: until the player allows it, no pack effect is drawn and the effect list is not reported to the server.
- An effect is validated before its first use (chain exists, shaders compile) because an error while loading a post chain in the vanilla shader loader resets the player's resource packs. Results are cached until the next shader reload; failed effects are skipped and reported in the status.
- `EffectRenderer.render` catches errors, logs once and switches drawing off until the game restarts instead of failing every frame.
- A layer removed by the server fades out smoothly instead of disappearing; the drawing order is by priority with a stable sort.
- Effect ids from the server are accepted only if they match a strict pattern (built-in) or exist in the player's resources (pack effects), because they go into `Identifier`s and shader paths.
- The command root is the same as the server plugin's `/posteffectcontrol`; Fabric forwards subcommands unknown to the client to the server. `/pec` is a client-only alias.

## License
All rights reserved.
