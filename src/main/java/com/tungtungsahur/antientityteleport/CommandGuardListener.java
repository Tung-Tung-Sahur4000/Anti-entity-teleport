package com.tungtungsahur.antientityteleport;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
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
 * <p>The first time a command containing a guarded selector is seen it is
 * cancelled and the sender is warned. Re-typing the exact same command within
 * the confirmation window lets it run untouched -- the vanilla command is its
 * own confirmation, so there is no extra command to learn.</p>
 */
public final class CommandGuardListener implements Listener {

    private final AntiEntityTeleport plugin;

    public CommandGuardListener(AntiEntityTeleport plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String withoutSlash = stripLeadingSlash(event.getMessage());

        if (shouldBlock(player, withoutSlash)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        CommandSender sender = event.getSender();
        String withoutSlash = stripLeadingSlash(event.getCommand());

        if (shouldBlock(sender, withoutSlash)) {
            event.setCancelled(true);
        }
    }

    /**
     * Decides whether the command should be blocked this time.
     *
     * @return {@code true} if the caller should cancel the event (first sight of
     *         a guarded command); {@code false} if it is safe or is a confirmed
     *         re-type that should be allowed to run.
     */
    private boolean shouldBlock(CommandSender sender, String withoutSlash) {
        AntiEntityTeleport.GuardedSelector match = plugin.findGuardedSelector(withoutSlash);
        if (match == null) {
            return false; // nothing dangerous here
        }
        if (isBypassing(sender)) {
            return false; // trusted sender, run immediately
        }

        String normalized = plugin.normalizeCommand(withoutSlash);

        // Second, identical run within the window -> confirmed, let it through.
        if (plugin.consumeIfMatches(sender, normalized)) {
            sender.sendMessage(plugin.prefix() + plugin.message("confirmed"));
            if (plugin.isLogToConsole()) {
                plugin.getLogger().info(sender.getName() + " confirmed guarded command: /" + withoutSlash);
            }
            return false;
        }

        // First sight -> remember it and warn.
        plugin.storePending(sender, normalized);
        warn(sender, withoutSlash, match);
        return true;
    }

    /** Warns the sender and tells them how to confirm (re-type / click). */
    private void warn(CommandSender sender, String withoutSlash, AntiEntityTeleport.GuardedSelector match) {
        if (plugin.isLogToConsole()) {
            plugin.getLogger().warning(sender.getName() + " tried to run a guarded command ("
                    + match.raw() + "): /" + withoutSlash + " -> awaiting confirmation.");
        }

        sender.sendMessage(plugin.prefix()
                + plugin.message("warning", "{selector}", match.raw()));
        sender.sendMessage(plugin.prefix()
                + plugin.message("how-to-confirm",
                "{timeout}", String.valueOf(plugin.getConfirmationTimeoutSeconds())));

        // Give players a clickable shortcut that simply re-runs the same command.
        if (sender instanceof Player) {
            sendClickableConfirm((Player) sender, withoutSlash);
        }
    }

    private void sendClickableConfirm(Player player, String withoutSlash) {
        TextComponent component = new TextComponent(
                new ComponentBuilder(plugin.prefix()).create());
        TextComponent button = new TextComponent(
                new ComponentBuilder(plugin.message("click-to-confirm")).create());
        // Clicking re-runs the very same vanilla command, which comes back
        // through this listener and matches the pending entry -> it runs.
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + withoutSlash));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("Runs /" + withoutSlash + " again")));
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
