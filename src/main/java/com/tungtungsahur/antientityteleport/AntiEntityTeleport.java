package com.tungtungsahur.antientityteleport;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Main plugin class for AntiEntityTeleport.
 *
 * <p>Every command a player or the console runs is inspected. If it contains a
 * guarded target selector (by default {@code @e}, which targets ALL entities in
 * the world) it is blocked. To actually run it you re-type the same command
 * with the word {@code confirm} added to the end:</p>
 *
 * <pre>
 *   /tp @e noobgamer23           -&gt; blocked, asks you to confirm
 *   /tp @e noobgamer23 confirm   -&gt; runs /tp @e noobgamer23
 * </pre>
 *
 * <p>This turns the classic "I typed /tp @e instead of /tp @a" disaster into a
 * harmless double-check, and works for kill, tp, data, or any other command
 * that can target {@code @e}.</p>
 */
public final class AntiEntityTeleport extends JavaPlugin {

    /** Compiled matchers for every guarded selector, built from the config. */
    private List<GuardedSelector> guardedSelectors = new ArrayList<>();

    private final FunctionScanner functionScanner = new FunctionScanner(this);

    private String confirmationKeyword;
    private boolean allowBypassPermission;
    private boolean logToConsole;
    private boolean scanFunctions;
    private boolean confirmUnreadableFunctions;
    private boolean guardCommandBlocks;

    /** Home-plugin maintenance notice settings. */
    private boolean homeMaintenanceEnabled;
    private List<String> maintenanceCommands = new ArrayList<>();
    private ZoneId maintenanceZone = ZoneId.of(DEFAULT_MAINTENANCE_TIMEZONE);
    private String maintenanceTimezoneLabel;
    private int maintenanceEtaHours;
    private boolean maintenanceBypassAllowed;

    private static final String DEFAULT_MAINTENANCE_TIMEZONE = "Asia/Kolkata";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(new CommandGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new HomeMaintenanceListener(this), this);

        getLogger().info("Enabled. Guarding selectors: " + describeGuardedSelectors()
                + " (confirm with the word '" + confirmationKeyword + "').");
        if (homeMaintenanceEnabled) {
            getLogger().info("Home maintenance notice is ON for: " + describeMaintenanceCommands()
                    + " (ETA = now + " + maintenanceEtaHours + "h, " + maintenanceTimezoneLabel + ").");
        }
    }

    /** (Re)reads all values from config.yml into memory. */
    public void loadSettings() {
        reloadConfig();

        List<String> rawSelectors = getConfig().getStringList("guarded-selectors");
        if (rawSelectors.isEmpty()) {
            rawSelectors = new ArrayList<>();
            rawSelectors.add("@e");
        }

        List<GuardedSelector> compiled = new ArrayList<>();
        for (String selector : rawSelectors) {
            if (selector == null || selector.trim().isEmpty()) {
                continue;
            }
            compiled.add(new GuardedSelector(selector.trim()));
        }
        this.guardedSelectors = compiled;

        String keyword = getConfig().getString("confirmation-keyword", "confirm");
        if (keyword == null || keyword.trim().isEmpty()) {
            keyword = "confirm";
        }
        this.confirmationKeyword = keyword.trim();
        this.allowBypassPermission = getConfig().getBoolean("allow-bypass-permission", true);
        this.logToConsole = getConfig().getBoolean("log-to-console", true);
        this.scanFunctions = getConfig().getBoolean("scan-functions", true);
        this.confirmUnreadableFunctions = getConfig().getBoolean("confirm-unreadable-functions", false);
        this.guardCommandBlocks = getConfig().getBoolean("guard-command-blocks", false);

        loadMaintenanceSettings();
    }

    /** Reads the {@code home-maintenance} section of config.yml. */
    private void loadMaintenanceSettings() {
        this.homeMaintenanceEnabled = getConfig().getBoolean("home-maintenance.enabled", true);

        List<String> rawCommands = getConfig().getStringList("home-maintenance.commands");
        if (rawCommands.isEmpty()) {
            rawCommands = new ArrayList<>();
            rawCommands.add("home");
        }
        List<String> normalized = new ArrayList<>();
        for (String command : rawCommands) {
            // Store bare, lowercase labels so "/home", "Home" and "essentials:home"
            // all compare equal to what the listener extracts from a command line.
            String label = HomeMaintenanceListener.rootLabel(command);
            if (!label.isEmpty() && !normalized.contains(label)) {
                normalized.add(label);
            }
        }
        this.maintenanceCommands = normalized;

        String zoneId = getConfig().getString("home-maintenance.timezone", DEFAULT_MAINTENANCE_TIMEZONE);
        try {
            this.maintenanceZone = ZoneId.of(zoneId == null ? DEFAULT_MAINTENANCE_TIMEZONE : zoneId.trim());
        } catch (DateTimeException ex) {
            getLogger().warning("Unknown timezone '" + zoneId + "' in home-maintenance.timezone; "
                    + "falling back to " + DEFAULT_MAINTENANCE_TIMEZONE + " (IST).");
            this.maintenanceZone = ZoneId.of(DEFAULT_MAINTENANCE_TIMEZONE);
        }

        String label = getConfig().getString("home-maintenance.timezone-label", "IST");
        this.maintenanceTimezoneLabel = (label == null || label.trim().isEmpty()) ? "IST" : label.trim();

        int etaHours = getConfig().getInt("home-maintenance.eta-hours", 2);
        this.maintenanceEtaHours = etaHours < 0 ? 0 : etaHours;

        this.maintenanceBypassAllowed =
                getConfig().getBoolean("home-maintenance.allow-bypass-permission", true);
    }

    /**
     * Returns the guarded selector that appears in the given command, or
     * {@code null} if the command is safe. Only the first match is reported
     * since one warning is enough.
     *
     * @param command the raw command WITHOUT a leading slash
     */
    public GuardedSelector findGuardedSelector(String command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        for (GuardedSelector selector : guardedSelectors) {
            if (selector.matches(command)) {
                return selector;
            }
        }
        return null;
    }

    /** True if the command's last word is the confirmation keyword. */
    public boolean hasConfirmationSuffix(String command) {
        if (command == null) {
            return false;
        }
        String trimmed = command.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return false; // just one word, so no real command before "confirm"
        }
        String lastWord = trimmed.substring(lastSpace + 1);
        return lastWord.equalsIgnoreCase(confirmationKeyword);
    }

    /**
     * Removes the trailing confirmation keyword and returns the real command
     * that should actually be run. Assumes {@link #hasConfirmationSuffix} is true.
     */
    public String stripConfirmationSuffix(String command) {
        String trimmed = command.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        return trimmed.substring(0, lastSpace).trim();
    }

    // ---- Config accessors -------------------------------------------

    public boolean isBypassAllowed() {
        return allowBypassPermission;
    }

    public boolean isLogToConsole() {
        return logToConsole;
    }

    public String getConfirmationKeyword() {
        return confirmationKeyword;
    }

    public boolean isScanFunctions() {
        return scanFunctions;
    }

    public boolean isConfirmUnreadableFunctions() {
        return confirmUnreadableFunctions;
    }

    public boolean isGuardCommandBlocks() {
        return guardCommandBlocks;
    }

    public FunctionScanner getFunctionScanner() {
        return functionScanner;
    }

    public boolean isHomeMaintenanceEnabled() {
        return homeMaintenanceEnabled;
    }

    public boolean isMaintenanceBypassAllowed() {
        return maintenanceBypassAllowed;
    }

    /**
     * True if the given bare command label (already lowercase, no slash and no
     * "plugin:" namespace) is gated by the maintenance notice.
     */
    public boolean isMaintenanceCommand(String label) {
        return label != null && !label.isEmpty() && maintenanceCommands.contains(label);
    }

    public ZoneId getMaintenanceZone() {
        return maintenanceZone;
    }

    public String getMaintenanceTimezoneLabel() {
        return maintenanceTimezoneLabel;
    }

    public int getMaintenanceEtaHours() {
        return maintenanceEtaHours;
    }

    /** Fetches a message from config, applies color codes and known placeholders. */
    public String message(String key, String... replacements) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw == null) {
            raw = "";
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String prefix() {
        return message("prefix");
    }

    private String describeGuardedSelectors() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guardedSelectors.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(guardedSelectors.get(i).raw());
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private String describeMaintenanceCommands() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maintenanceCommands.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('/').append(maintenanceCommands.get(i));
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    /** A single guarded selector plus its compiled whole-token matcher. */
    public static final class GuardedSelector {
        private final String raw;
        private final Pattern pattern;

        GuardedSelector(String raw) {
            this.raw = raw;
            // Match the selector as a whole token: it must not be immediately
            // followed by a word character, so "@e" and "@e[type=item]" match
            // but "@executor" does not.
            this.pattern = Pattern.compile(Pattern.quote(raw) + "(?![A-Za-z0-9_])");
        }

        public boolean matches(String command) {
            return pattern.matcher(command).find();
        }

        public String raw() {
            return raw;
        }
    }
}
