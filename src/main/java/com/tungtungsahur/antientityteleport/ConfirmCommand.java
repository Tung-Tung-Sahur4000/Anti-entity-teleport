package com.tungtungsahur.antientityteleport;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles {@code /aetp confirm}, {@code /aetp cancel} and {@code /aetp reload}.
 */
public final class ConfirmCommand implements CommandExecutor, TabCompleter {

    private final AntiEntityTeleport plugin;

    public ConfirmCommand(AntiEntityTeleport plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "confirm" : args[0].toLowerCase();

        switch (sub) {
            case "confirm":
                return handleConfirm(sender);
            case "cancel":
                return handleCancel(sender);
            case "reload":
                return handleReload(sender);
            default:
                sender.sendMessage(plugin.prefix() + "Usage: /aetp <confirm|cancel|reload>");
                return true;
        }
    }

    private boolean handleConfirm(CommandSender sender) {
        if (!plugin.hasPending(sender)) {
            sender.sendMessage(plugin.prefix() + plugin.message("nothing-pending"));
            return true;
        }

        PendingCommand entry = plugin.takePending(sender);
        if (entry == null) {
            // Present a moment ago but expired between the check and now.
            sender.sendMessage(plugin.prefix() + plugin.message("expired"));
            return true;
        }

        sender.sendMessage(plugin.prefix() + plugin.message("confirmed"));
        // dispatchCommand runs the command directly and does NOT re-fire the
        // command events, so this cannot loop back into the guard.
        Bukkit.dispatchCommand(sender, entry.getCommand());
        return true;
    }

    private boolean handleCancel(CommandSender sender) {
        if (!plugin.hasPending(sender)) {
            sender.sendMessage(plugin.prefix() + plugin.message("nothing-pending"));
            return true;
        }
        plugin.clearPending(sender);
        sender.sendMessage(plugin.prefix() + plugin.message("cancelled"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("antientityteleport.admin")) {
            sender.sendMessage(plugin.prefix() + plugin.message("no-permission"));
            return true;
        }
        plugin.loadSettings();
        sender.sendMessage(plugin.prefix() + plugin.message("reloaded"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("confirm", "cancel"));
            if (sender.hasPermission("antientityteleport.admin")) {
                options.add("reload");
            }
            List<String> matches = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (String option : options) {
                if (option.startsWith(prefix)) {
                    matches.add(option);
                }
            }
            return matches;
        }
        return new ArrayList<>();
    }
}
