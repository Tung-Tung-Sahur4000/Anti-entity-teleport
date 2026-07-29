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

This works for **players typing in chat** and the **console** — both fire a
Bukkit command event that the plugin listens to.

## Functions (a whole chain of commands)

A function isn't one command — it's a chain, and it can call other functions
and `#tags`. The individual commands inside a `.mcfunction` file never fire a
Bukkit event, so they can't be caught one-by-one. Instead, when someone runs
the entry point:

```
/function my:cleanup
/execute as @a at @s run function my:cleanup
```

the plugin **reads the function file from the datapack and scans the entire
chain** — following nested `function` calls and function `#tags` (with cycle
protection) — and if **any** command in that chain targets `@e`, the whole
invocation is blocked:

```
[AntiEntityTeleport] ⚠ Function my:cleanup runs a command targeting @e (ALL entities), in my:cleanup.
[AntiEntityTeleport] Re-type the same command with confirm on the end to run it. Example: /function my:cleanup confirm
```

Confirm the same way: `/function my:cleanup confirm`.

Functions that live inside the server jar / bundled datapacks can't be read from
disk; by default those are allowed through (they're pre-written, not typos). Set
`confirm-unreadable-functions: true` if you want to be asked about those too.

## Home plugin maintenance notice

While the Home plugin is being worked on, `/home` is **intercepted before it
gets there**. The command is cancelled and the player is told what's going on,
with an **ETA that is computed from the clock**, not hard-coded:

```
[AntiEntityTeleport] ⚠ Home plugin under maintenance, be right back!
[AntiEntityTeleport] ETA 2:00 PM IST (it's 12:00 PM IST now -- about 2h away).
```

The ETA is *the current time in IST plus `eta-hours` (2 by default)*, so it
stays correct all day:

| Player runs `/home` at | Message says ETA |
| --- | --- |
| 12:00 PM IST | 2:00 PM IST |
| 3:00 PM IST | 5:00 PM IST |
| 9:45 AM IST | 11:45 AM IST |

### Getting in front of the Home plugin

Bukkit's priority names are inverted from intuition: **`LOWEST` runs first** and
`HIGHEST` runs last. To be sure the Home plugin never gets the command, this
listener takes both ends:

| Where | Priority | What it does |
| --- | --- | --- |
| First word | `LOWEST` | Cancels `/home` and sends the notice, before any other listener or the Home plugin's own command executor runs. |
| Last word | `HIGHEST` | If a plugin further down the chain called `setCancelled(false)` to put the command back, cancels it again — silently, since the player was already told. |

Two more details make the ordering airtight:

* The `LOWEST` handler uses `ignoreCancelled = false`, so even if a home plugin
  cancelled the event at `LOWEST` before us and handled `/home` internally, we
  still send the maintenance notice instead of staying quiet.
* Handlers on the *same* priority run in **plugin load order**, so `plugin.yml`
  declares `loadbefore` for the common home providers (Essentials, CMI,
  HuskHomes, …). Missing plugins are ignored; add your own home plugin's name
  to that list if it isn't there.

`PlayerCommandPreprocessEvent` fires before Bukkit dispatches the command at
all, so cancelling it stops the Home plugin's `onCommand` from ever being
called — there's no window in which it can run first.

Matching is on the bare command label, ignoring case and any `plugin:` prefix —
so `/home`, `/Home`, `/home base` and `/essentials:home` are all caught, while
`/sethome` and `/homes` are not (add them to `home-maintenance.commands` if you
want them gated too). Command blocks are skipped, since they can't read chat.

Staff who need to actually test the Home plugin can be given
`antientityteleport.maintenance.bypass`. When the plugin is back, set
`home-maintenance.enabled: false` and reload.

## Command blocks

Bukkit provides **no event** for a command block executing a command, so a
command block running `/kill @e` cannot be intercepted at execution time without
NMS or ProtocolLib packet manipulation — and "confirm in chat" is meaningless
for an automated, redstone-triggered block anyway. Command blocks are therefore
**left alone by default** (see `guard-command-blocks`) so this plugin never
silently breaks a working contraption. If you want command-block protection,
that needs a heavier packet/NMS approach — open an issue and we can discuss it.

## Supported versions

Built against the Bukkit API using only long-stable APIs and compiled to **Java 17
bytecode**, so the same single jar runs on every supported version:

| Server software | Versions | Server Java |
| --- | --- | --- |
| **Paper** (recommended) | **1.20, 1.21, 26.1** | 17 for 1.20–1.20.4; 21 for 1.20.5+, 1.21 and 26.1 |
| Spigot / Bukkit | 1.20, 1.21, 26.1 | same |
| Purpur, Pufferfish, other Paper forks | same as Paper | same |

The jar targets Java 17 bytecode, which loads on any Java 17+ runtime — so it
covers 1.20 (Java 17) all the way up through 1.21 and 26.1 (Java 21) from a
single build. No per-version jar is needed.

> The `@e` / function / command-block behavior described above is identical on
> every supported version. Function scanning also handles the datapack folder
> rename (`functions/` on 1.20, `function/` on 1.21+) automatically.

## Permissions

| Permission | Default | Meaning |
| --- | --- | --- |
| `antientityteleport.bypass` | `false` | Skip confirmation and run `@e` commands immediately. Give only to people you trust. |
| `antientityteleport.maintenance.bypass` | `false` | Use `/home` normally while the Home plugin is flagged as under maintenance. |

## Configuration (`config.yml`)

```yaml
guarded-selectors:
  - "@e"                          # add more if you like, e.g. "@r"
confirmation-keyword: "confirm"
scan-functions: true             # read /function chains and gate @e inside them
confirm-unreadable-functions: false
guard-command-blocks: false      # leave command blocks alone (see above)
allow-bypass-permission: true    # honor antientityteleport.bypass
log-to-console: true             # print a warning when a command is caught
home-maintenance:
  enabled: true                  # intercept /home with a maintenance notice
  commands: ["home"]             # labels to gate (case- and namespace-insensitive)
  timezone: "Asia/Kolkata"       # IST; the ETA clock is read in this zone
  timezone-label: "IST"          # short name shown in the message
  eta-hours: 2                   # ETA = current time + this many hours
  allow-bypass-permission: true  # honor antientityteleport.maintenance.bypass
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
Drop it into your server's `plugins/` folder and restart. It is compiled
against the Spigot API 1.20.4 and emits Java 17 bytecode, so the one jar runs on
Paper/Spigot 1.20, 1.21 and 26.1 (see **Supported versions** above). Your server
needs Java 17 (for 1.20–1.20.4) or Java 21 (for 1.20.5+, 1.21 and 26.1).

### Don't want to build it yourself?

Every push is built automatically by GitHub Actions (`.github/workflows/build.yml`).
Grab the compiled jar from the **Actions** tab → pick the latest run → download the
**AntiEntityTeleport** artifact. Pushing a tag like `v1.0.0` also attaches the jar
to a GitHub Release.
