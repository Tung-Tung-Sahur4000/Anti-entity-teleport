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

    // LOWEST so the command is stopped before anything else (including the @e
    // guard) gets a chance to look at it.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (isUnderMaintenance(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
            announce(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
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
