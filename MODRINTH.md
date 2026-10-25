# PostEffectControl

Client mod that draws screen effects sent by a server (freeze, blur, glitch and more), with an emergency off switch, a strength cap and hiding of flashing effects.

## Features
- 12 built-in effects, such as freeze, blur, grayscale, vignette, wobble, glitch and heartbeat
- Effects from your own resource packs, only if you allow them
- Smooth fade-in and fade-out of every effect
- Emergency switch: `/posteffectcontrol off` hides everything immediately
- Maximum strength setting and a setting that hides flashing effects (for people sensitive to flashing light)
- Your settings cannot be overridden by the server

## Requirements
- Minecraft 1.21.11, Fabric Loader, Fabric API
- The mod does nothing on its own: it draws the effects that a server with the `PostEffectControlPlugin` plugin gives you. It works on any other server too, but has nothing to draw there.

## Installation
1. Install Fabric Loader for Minecraft 1.21.11.
2. Put `PostEffectControl-<version>.jar` and Fabric API into the `mods` folder.
3. Join a server that uses `PostEffectControlPlugin`.

## Commands
The commands are `/posteffectcontrol` and the shorter `/pec`.

| Command | Description |
|---|---|
| `/posteffectcontrol off` | Hide all effects immediately |
| `/posteffectcontrol on` | Show effects again |
| `/posteffectcontrol status` | Show the current settings and the number of active effects |
| `/posteffectcontrol max <0..1>` | Cap the strength of every effect (`1` = no cap, `0` = invisible) |
| `/posteffectcontrol noflash <true\|false>` | Hide effects that are marked as flashing |
| `/posteffectcontrol packeffects <true\|false>` | Allow the server to use effects from your resource packs |

Other `/posteffectcontrol` subcommands are passed to the server.

## Configuration
File: `config/posteffectcontrol.json` (created on first change; edit it while the game is closed)

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `enabled` | boolean | `true` | `true`, `false` | Master switch; `false` is the same as `/posteffectcontrol off` |
| `maxIntensity` | number | `1.0` | `0.0..1.0` | Cap for the strength of every effect |
| `reduceFlashing` | boolean | `false` | `true`, `false` | Hide flashing effects |
| `allowPackEffects` | boolean | `false` | `true`, `false` | Allow effects from your resource packs |

```json
{
  "enabled": true,
  "maxIntensity": 0.6,
  "reduceFlashing": true,
  "allowPackEffects": false
}
```

## Controls
None.

## Privacy and safety
- The server sends only the names of effects and their settings. No shaders or other files are sent: built-in effects are inside the mod, and effects from resource packs must already be in your own packs.
- The server learns whether effects work, whether Iris is installed, whether you have effects switched on, how many effects are active, and a short technical error text if something fails.
- Effects from your resource packs are off by default. If you turn them on with `/posteffectcontrol packeffects true`, the names and parameters of those effects are sent to the server. Turn them off with `/posteffectcontrol packeffects false`.
- An effect is checked before its first use. If it fails, it is skipped and the rest of the game keeps working.
- Whether an effect flashes is decided by your own files and the mod, never by the server. `noflash` hides all of them.
- To turn everything off at once, use `/posteffectcontrol off`.

## Troubleshooting
| Problem | Solution |
|---|---|
| I see no effects | Run `/posteffectcontrol status`. If effects are off, use `/posteffectcontrol on`. If the strength cap is `0`, raise it with `/posteffectcontrol max 1` |
| A flashing effect is missing | `noflash` is on: turn it off with `/posteffectcontrol noflash false` if you want to see it |
| Effects from my resource packs do not work | Allow them with `/posteffectcontrol packeffects true` and check that the pack is enabled |
| An effect looks wrong with shader mods | Switch effects off with `/posteffectcontrol off` and tell the server administrator |
| Mod and server do not work together | Use mod and plugin versions with the same major version (2.x) |

## License
All rights reserved.

## Русский
Клиентский мод, который рисует эффекты экрана, присланные сервером (заморозка, размытие, глитч и другие), с аварийным выключателем, ограничением силы и скрытием мигающих эффектов.

### Возможности
- 12 встроенных эффектов: freeze, blur, grayscale, vignette, wobble, glitch, heartbeat и другие
- Эффекты из ваших ресурспаков, только если вы это разрешили
- Плавное появление и затухание эффектов
- Аварийный выключатель: `/posteffectcontrol off` сразу скрывает всё
- Ограничение максимальной силы и скрытие мигающих эффектов (для людей, чувствительных к мерцанию)
- Сервер не может обойти ваши настройки

### Требования и установка
Minecraft 1.21.11, Fabric Loader, Fabric API. Сам по себе мод ничего не делает: он рисует эффекты, которые выдаёт сервер с плагином `PostEffectControlPlugin`. Положите `PostEffectControl-<версия>.jar` и Fabric API в папку `mods`.

### Команды
Команды `/posteffectcontrol` и короткая `/pec`:
- `off` и `on` — скрыть все эффекты сразу или показать снова;
- `status` — показать настройки и число активных эффектов;
- `max <0..1>` — ограничить силу любого эффекта;
- `noflash <true|false>` — скрывать мигающие эффекты;
- `packeffects <true|false>` — разрешить серверу использовать эффекты из ваших ресурспаков.

Остальные подкоманды `/posteffectcontrol` передаются серверу.

### Настройки
Файл `config/posteffectcontrol.json` (редактируйте при закрытой игре): `enabled` (`true`), `maxIntensity` (`1.0`, диапазон `0.0..1.0`), `reduceFlashing` (`false`), `allowPackEffects` (`false`).

### Приватность и безопасность
- Сервер присылает только названия эффектов и их настройки. Шейдеры и другие файлы по сети не передаются.
- Серверу сообщается, работают ли эффекты, установлен ли Iris, включены ли у вас эффекты, сколько их активно, и короткий технический текст ошибки, если что-то не сработало.
- Эффекты из ваших ресурспаков по умолчанию выключены. Если включить их командой `/posteffectcontrol packeffects true`, серверу отправляются названия и параметры этих эффектов.
- Эффект проверяется перед первым использованием; если он не работает, он пропускается, а игра продолжает работать.
- Мигает ли эффект, определяют ваши файлы и мод, а не сервер. `noflash` скрывает все такие эффекты.
- Чтобы всё выключить сразу, используйте `/posteffectcontrol off`.

### Решение проблем
- Эффектов не видно: `/posteffectcontrol status`; при необходимости `/posteffectcontrol on`; если ограничение силы равно 0, поднимите его `/posteffectcontrol max 1`.
- Не видно мигающий эффект: включён `noflash`, выключите его командой `/posteffectcontrol noflash false`.
- Эффекты из ресурспаков не работают: разрешите их командой `/posteffectcontrol packeffects true` и проверьте, что пак включён.
- Используйте версии мода и плагина с одинаковой старшей версией (2.x).
