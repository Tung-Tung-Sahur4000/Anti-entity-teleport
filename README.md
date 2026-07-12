# AntiEntityTeleport

A tiny Bukkit / Spigot / Paper plugin that puts a safety net under the most
dangerous typo in Minecraft: typing **`@e`** (every entity in the world) when
you meant `@a` (all players), `@p`, or a player's name.

> You meant `/tp @a noobgamer23`.
> You typed `/tp @e noobgamer23`.
> Every mob, item, armor stand and painting just got yanked across the map.

With this plugin, **the first time you run a command containing `@e` it is
blocked** and you have to run the **exact same command again to confirm** — so a
slip of the finger can never wreck your world again. There is **no custom
command to learn**: the vanilla command itself is the confirmation, and it all
works through event listeners.

## How it works

1. You run a vanilla command that contains `@e`
   (e.g. `/tp @e noobgamer23`, `/kill @e`).
2. A listener **cancels it before it does anything** and warns you:

   ```
   [AntiEntityTeleport] ⚠ Whoa! Your command targets @e (ALL entities). Double-check this is what you want.
   [AntiEntityTeleport] Type the exact same command again within 30s to run it. Do nothing to cancel.
   [AntiEntityTeleport] [Click here to run it again & CONFIRM]
   ```

3. If you really meant it, **run the same command again** (press ↑ then Enter,
   or click the button). This second, identical run within the window is
   allowed through and executes exactly as typed.
4. If it was a typo, just do nothing — the pending command expires and nothing
   happens.

Matching is whole-token, so `@e` and `@e[type=item]` are caught, while things
like `@a`, `@p`, `@s` and words such as `@executor` are left alone.

## Permissions

| Permission | Default | Meaning |
| --- | --- | --- |
| `antientityteleport.bypass` | `false` | Skip confirmation and run `@e` commands immediately. Give only to people you trust. |

## Configuration (`config.yml`)

```yaml
guarded-selectors:
  - "@e"                       # add more if you like, e.g. "@r"
confirmation-timeout-seconds: 30
allow-bypass-permission: true  # honor antientityteleport.bypass
log-to-console: true           # print a warning when a command is caught
messages:
  # fully customizable, & color codes supported
```

Change any value, then reload the plugin (e.g. server restart, or `/reload`).

## Building

Requires JDK 17+ and Maven.

```bash
mvn clean package
```

The finished plugin is written to `target/AntiEntityTeleport-1.0.0.jar`.
Drop it into your server's `plugins/` folder and restart. Tested against the
Spigot API 1.20.4 and compatible with Paper and any 1.16+ server.
