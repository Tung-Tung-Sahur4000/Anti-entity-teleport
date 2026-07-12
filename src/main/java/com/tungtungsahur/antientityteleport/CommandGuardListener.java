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
 * Intercepts commands from players and the console before they run and holds
 * back anything containing a guarded selector until it is confirmed.
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

        // Never intercept our own confirm/cancel command, or the player could
        // never confirm anything.
        if (isOwnCommand(withoutSlash)) {
            return;
        }

        AntiEntityTeleport.GuardedSelector match = plugin.findGuardedSelector(withoutSlash);
        if (match == null) {
            return;
        }
        if (isBypassing(player)) {
            return;
        }

        event.setCancelled(true);
        guard(player, withoutSlash, match);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        CommandSender sender = event.getSender();
        String withoutSlash = stripLeadingSlash(event.getCommand());

        if (isOwnCommand(withoutSlash)) {
            return;
        }

        AntiEntityTeleport.GuardedSelector match = plugin.findGuardedSelector(withoutSlash);
        if (match == null) {
            return;
        }
        if (isBypassing(sender)) {
            return;
        }

        event.setCancelled(true);
        guard(sender, withoutSlash, match);
    }

    /** Stores the command and warns the sender how to confirm it. */
    private void guard(CommandSender sender, String withoutSlash, AntiEntityTeleport.GuardedSelector match) {
        plugin.storePending(sender, withoutSlash);

        if (plugin.isLogToConsole()) {
            plugin.getLogger().warning(sender.getName() + " tried to run a guarded command ("
                    + match.raw() + "): /" + withoutSlash + " -> awaiting confirmation.");
        }

        sender.sendMessage(plugin.prefix()
                + plugin.message("warning", "{selector}", match.raw()));
        sender.sendMessage(plugin.prefix()
                + plugin.message("how-to-confirm",
                "{timeout}", String.valueOf(plugin.getConfirmationTimeoutSeconds())));

        // Give players a clickable shortcut. Console just uses the text above.
        if (sender instanceof Player) {
            sendClickableConfirm((Player) sender);
        }
    }

    private void sendClickableConfirm(Player player) {
        TextComponent component = new TextComponent(
                new ComponentBuilder(plugin.prefix()).create());
        TextComponent button = new TextComponent(
                new ComponentBuilder(plugin.message("click-to-confirm")).create());
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/aetp confirm"));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("Runs /aetp confirm")));
        component.addExtra(button);
        player.spigot().sendMessage(component);
    }

    private boolean isBypassing(CommandSender sender) {
        return plugin.isBypassAllowed() && sender.hasPermission("antientityteleport.bypass");
    }

    private boolean isOwnCommand(String withoutSlash) {
        String lower = withoutSlash.toLowerCase();
        return lower.equals("aetp") || lower.startsWith("aetp ")
                || lower.equals("antientityteleport") || lower.startsWith("antientityteleport ")
                || lower.equals("entityconfirm") || lower.startsWith("entityconfirm ");
    }

    private String stripLeadingSlash(String command) {
        if (command != null && command.startsWith("/")) {
            return command.substring(1);
        }
        return command == null ? "" : command;
    }
}
