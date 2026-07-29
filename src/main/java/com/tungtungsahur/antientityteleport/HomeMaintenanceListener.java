package com.tungtungsahur.antientityteleport;

import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Intercepts the home commands while the Home plugin is down for maintenance.
 *
 * <p>Instead of letting {@code /home} fall through to a plugin that is being
 * worked on (or is not loaded at all, which just prints "Unknown command"), the
 * command is cancelled and the sender is told it is under maintenance, together
 * with an ETA.</p>
 *
 * <p>The ETA is <em>not</em> a fixed string: it is the current wall-clock time
 * in the configured timezone (IST by default) plus {@code eta-hours}. So at
 * 12:00 PM IST it reads 2:00 PM, at 3:00 PM IST it reads 5:00 PM.</p>
 */
public final class HomeMaintenanceListener implements Listener {

    /** 12-hour clock, e.g. "2:00 PM". Locale-fixed so it reads the same everywhere. */
    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final AntiEntityTeleport plugin;

    public HomeMaintenanceListener(AntiEntityTeleport plugin) {
        this.plugin = plugin;
    }

    // ---- First word ------------------------------------------------
    // LOWEST runs BEFORE every other priority, so we cancel the command before
    // the Home plugin's own listener or its command executor ever sees it.
    // ignoreCancelled = false on purpose: if a home plugin also registered at
    // LOWEST and got in ahead of us (same-priority ties are broken by plugin
    // load order), the event arrives already cancelled -- we still want to say
    // why, instead of silently letting its own handling stand.

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerCommandEarly(PlayerCommandPreprocessEvent event) {
        if (isUnderMaintenance(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
            announce(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onServerCommandEarly(ServerCommandEvent event) {
        // A command block can't read chat, so telling it about maintenance is
        // pointless; leave it alone exactly like the @e guard does.
        if (event.getSender() instanceof BlockCommandSender) {
            return;
        }
        if (isUnderMaintenance(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
            announce(event.getSender());
        }
    }

    // ---- Last word -------------------------------------------------
    // A plugin further down the chain is allowed to call setCancelled(false)
    // and put the command back. HIGHEST runs after every normal handler, so
    // re-cancelling here means the maintenance gate has the final say. Silent:
    // the sender was already told at LOWEST.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerCommandLate(PlayerCommandPreprocessEvent event) {
        if (!event.isCancelled() && isUnderMaintenance(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
            logOverride(event.getPlayer(), event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onServerCommandLate(ServerCommandEvent event) {
        if (event.getSender() instanceof BlockCommandSender) {
            return;
        }
        if (!event.isCancelled() && isUnderMaintenance(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
            logOverride(event.getSender(), event.getCommand());
        }
    }

    /** True if this command is one of the maintenance-gated ones for this sender. */
    private boolean isUnderMaintenance(CommandSender sender, String raw) {
        if (!plugin.isHomeMaintenanceEnabled()) {
            return false;
        }
        if (plugin.isMaintenanceBypassAllowed()
                && sender.hasPermission("antientityteleport.maintenance.bypass")) {
            return false; // staff testing the Home plugin still need to use it
        }
        return plugin.isMaintenanceCommand(rootLabel(raw));
    }

    /** Sends the maintenance notice plus the ETA computed from the current time. */
    private void announce(CommandSender sender) {
        ZonedDateTime now = ZonedDateTime.now(plugin.getMaintenanceZone());
        ZonedDateTime eta = now.plusHours(plugin.getMaintenanceEtaHours());

        String etaText = CLOCK.format(eta);
        String nowText = CLOCK.format(now);
        String zoneLabel = plugin.getMaintenanceTimezoneLabel();

        sender.sendMessage(plugin.prefix() + plugin.message("maintenance"));
        sender.sendMessage(plugin.prefix() + plugin.message("maintenance-eta",
                "{eta}", etaText,
                "{now}", nowText,
                "{timezone}", zoneLabel,
                "{hours}", String.valueOf(plugin.getMaintenanceEtaHours())));

        if (plugin.isLogToConsole()) {
            plugin.getLogger().info(sender.getName()
                    + " tried a home command while the Home plugin is under maintenance"
                    + " (now " + nowText + " " + zoneLabel + ", ETA " + etaText + " " + zoneLabel + ").");
        }
    }

    /**
     * Notes that some other plugin put a gated command back after we cancelled
     * it, and that we cancelled it again. Worth seeing in the log, since it
     * means another plugin is fighting the maintenance gate.
     */
    private void logOverride(CommandSender sender, String raw) {
        if (plugin.isLogToConsole()) {
            plugin.getLogger().warning("Another plugin un-cancelled '" + raw + "' from "
                    + sender.getName() + "; re-cancelled it (home maintenance has the final say).");
        }
    }

    /**
     * Reduces a raw command line to its bare command word, lowercased.
     *
     * <p>{@code "/essentials:home base"} and {@code "/Home"} both become
     * {@code "home"}. Returns an empty string when there is no command word.</p>
     */
    static String rootLabel(String command) {
        if (command == null) {
            return "";
        }
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        if (space >= 0) {
            trimmed = trimmed.substring(0, space);
        }
        // Drop any "plugin:" namespace so /essentials:home is caught too.
        int colon = trimmed.lastIndexOf(':');
        if (colon >= 0) {
            trimmed = trimmed.substring(colon + 1);
        }
        return trimmed.trim().toLowerCase(Locale.ROOT);
    }
}
