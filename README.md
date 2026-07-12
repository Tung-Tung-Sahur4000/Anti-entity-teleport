# AntiEntityTeleport

A tiny Bukkit / Spigot / Paper plugin that puts a safety net under the most
dangerous typo in Minecraft: typing **`@e`** (every entity in the world) when
you meant `@a` (all players), `@p`, or a player's name.

> You meant `/tp @a noobgamer23`.
> You typed `/tp @e noobgamer23`.
> Every mob, item, armor stand and painting just got yanked across the map.

With this plugin, **any command containing `@e` is paused and must be confirmed
before it runs** — so a slip of the finger can never wreck your world again.

## How it works

1. A player or the console runs a command that contains `@e`
   (e.g. `/tp @e noobgamer23`, `/kill @e`).
2. The command is **cancelled before it does anything** and a warning appears:

   ```
   [AntiEntityTeleport] ⚠ Whoa! Your command targets @e (ALL entities). Double-check this is what you want.
   [AntiEntityTeleport] Type /aetp confirm to run it, or /aetp cancel to drop it. Expires in 30s.
   [AntiEntityTeleport] [Click here to CONFIRM]
   ```

3. If you really meant it, run **`/aetp confirm`** (or click the button) and the
   original command runs exactly as typed. Otherwise it just expires — no harm done.

Matching is whole-token, so `@e` and `@e[type=item]` are caught, while things
like `@a`, `@p`, `@s` and words such as `@executor` are left alone.

## Commands

| Command | What it does |
| --- | --- |
| `/aetp confirm` | Run the command you were just warned about |
| `/aetp cancel` | Throw the pending command away |
| `/aetp reload` | Reload `config.yml` (needs `antientityteleport.admin`) |

Aliases: `/antientityteleport`, `/entityconfirm`.

## Permissions

| Permission | Default | Meaning |
| --- | --- | --- |
| `antientityteleport.bypass` | `false` | Skip confirmation and run `@e` commands immediately. Give only to people you trust. |
| `antientityteleport.admin` | `op` | Allowed to use `/aetp reload`. |

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

Change any value, then run `/aetp reload`.

## Building

Requires JDK 17+ and Maven.

```bash
mvn clean package
```

The finished plugin is written to `target/AntiEntityTeleport-1.0.0.jar`.
Drop it into your server's `plugins/` folder and restart. Tested against the
Spigot API 1.20.4 and compatible with Paper and any 1.16+ server.
