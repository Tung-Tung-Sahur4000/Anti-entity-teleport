package com.tungtungsahur.antientityteleport;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Intercepts vanilla commands from players and the console before they run.
 *
 * <p>Any command containing a guarded selector (e.g. {@code @e}) is blocked
 * unless it ends with the confirmation keyword. To run it you re-type the same
 * command with {@code confirm} on the end:</p>
 *
 * <pre>
 *   /tp @e noobgamer23           -&gt; blocked
 *   /tp @e noobgamer23 confirm   -&gt; runs /tp @e noobgamer23
 * </pre>
 */
public final class CommandGuardListener implements Listener {

    private final AntiEntityTeleport plugin;

    public CommandGuardListener(AntiEntityTeleport plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String withoutSlash = stripLeadingSlash(event.getMessage());
        if (handle(event.getPlayer(), withoutSlash)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String withoutSlash = stripLeadingSlash(event.getCommand());
        if (handle(event.getSender(), withoutSlash)) {
            event.setCancelled(true);
        }
    }

    /**
     * Core logic, shared by player and console.
     *
     * @return {@code true} if the original event must be cancelled (either the
     *         command was blocked, or it was the confirming variant that we run
     *         ourselves after stripping the keyword). {@code false} means the
     *         command is safe and should proceed untouched.
     */
    private boolean handle(CommandSender sender, String withoutSlash) {
        AntiEntityTeleport.GuardedSelector match = plugin.findGuardedSelector(withoutSlash);
        if (match == null) {
            return false; // nothing dangerous here
        }
        if (isBypassing(sender)) {
            return false; // trusted sender, run immediately
        }

        // "... confirm" -> strip the keyword and run the real command ourselves.
        if (plugin.hasConfirmationSuffix(withoutSlash)) {
            String realCommand = plugin.stripConfirmationSuffix(withoutSlash);

            // Guard against a lone "@e confirm" with nothing left to run.
            if (realCommand.isEmpty() || plugin.findGuardedSelector(realCommand) == null) {
                warn(sender, withoutSlash, match);
                return true;
            }

            sender.sendMessage(plugin.prefix() + plugin.message("confirmed"));
            if (plugin.isLogToConsole()) {
                plugin.getLogger().info(sender.getName() + " confirmed guarded command: /" + realCommand);
            }
            // dispatchCommand runs it directly and does NOT re-fire these events,
            // so it cannot loop back into the guard.
            Bukkit.dispatchCommand(sender, realCommand);
            return true; // cancel the raw "... confirm" so it doesn't error out
        }

        // First sight, no keyword -> block and explain.
        warn(sender, withoutSlash, match);
        return true;
    }

    /** Warns the sender and tells them to re-type the command with the keyword. */
    private void warn(CommandSender sender, String withoutSlash, AntiEntityTeleport.GuardedSelector match) {
        if (plugin.isLogToConsole()) {
            plugin.getLogger().warning(sender.getName() + " tried to run a guarded command ("
                    + match.raw() + "): /" + withoutSlash + " -> blocked, awaiting confirmation.");
        }

        sender.sendMessage(plugin.prefix()
                + plugin.message("warning", "{selector}", match.raw()));
        sender.sendMessage(plugin.prefix()
                + plugin.message("how-to-confirm",
                "{keyword}", plugin.getConfirmationKeyword(),
                "{command}", "/" + withoutSlash + " " + plugin.getConfirmationKeyword()));

        // Give players a clickable shortcut that adds the keyword for them.
        if (sender instanceof Player) {
            sendClickableConfirm((Player) sender, withoutSlash);
        }
    }

    private void sendClickableConfirm(Player player, String withoutSlash) {
        String confirmCommand = "/" + withoutSlash + " " + plugin.getConfirmationKeyword();

        TextComponent component = new TextComponent(
                new ComponentBuilder(plugin.prefix()).create());
        TextComponent button = new TextComponent(
                new ComponentBuilder(plugin.message("click-to-confirm")).create());
        // Clicking runs the same command with the keyword appended, which comes
        // back through this listener, gets stripped, and runs for real.
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, confirmCommand));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("Runs " + confirmCommand)));
        component.addExtra(button);
        player.spigot().sendMessage(component);
    }

    private boolean isBypassing(CommandSender sender) {
        return plugin.isBypassAllowed() && sender.hasPermission("antientityteleport.bypass");
    }

    private String stripLeadingSlash(String command) {
        if (command != null && command.startsWith("/")) {
            return command.substring(1);
        }
        return command == null ? "" : command;
    }
}
