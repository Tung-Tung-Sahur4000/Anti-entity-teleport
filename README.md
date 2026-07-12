# AntiEntityTeleport

A tiny Bukkit / Spigot / Paper plugin that puts a safety net under the most
dangerous typo in Minecraft: typing **`@e`** (every entity in the world) when
you meant `@a` (all players), `@p`, or a player's name.

> You meant `/tp @a noobgamer23`.
> You typed `/tp @e noobgamer23`.
> Every mob, item, armor stand and painting just got yanked across the map.

With this plugin, **any command containing `@e` is blocked** and only runs if
you re-type the **same command with the word `confirm` on the end** — so a slip
of the finger can never wreck your world again. There is **no custom command to
learn**: it works on any vanilla command (kill, tp, data, …) and is handled
entirely through event listeners.

## How it works

1. You run a vanilla command that contains `@e`
   (e.g. `/tp @e noobgamer23`, `/kill @e`).
2. A listener **cancels it before it does anything** and warns you:

   ```
   [AntiEntityTeleport] ⚠ Whoa! Your command targets @e (ALL entities). Double-check this is what you want.
   [AntiEntityTeleport] Re-type the same command with confirm on the end to run it. Example: /tp @e noobgamer23 confirm
   [AntiEntityTeleport] [Click here to add 'confirm' & run it]
   ```

3. If you really meant it, run the same command with **`confirm`** appended
   (or click the button):

   ```
   /tp @e noobgamer23 confirm
   ```

   The plugin strips the `confirm` and runs `/tp @e noobgamer23` exactly as typed.
4. If it was a typo, just do nothing — nothing happens.

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
confirmation-keyword: "confirm"
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
