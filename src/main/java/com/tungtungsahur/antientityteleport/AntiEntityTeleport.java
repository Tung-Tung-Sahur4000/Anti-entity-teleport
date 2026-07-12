package com.tungtungsahur.antientityteleport;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Main plugin class for AntiEntityTeleport.
 *
 * <p>Every command a player or the console runs is inspected. If it contains a
 * guarded target selector (by default {@code @e}, which targets ALL entities in
 * the world) the command is cancelled and the sender must confirm it with
 * {@code /aetp confirm}. This turns the classic "I typed /tp @e instead of
 * /tp @a" disaster into a harmless double-check.</p>
 */
public final class AntiEntityTeleport extends JavaPlugin {

    /** Pending commands keyed by a stable id for the sender (see {@link #senderKey}). */
    private final Map<String, PendingCommand> pending = new ConcurrentHashMap<>();

    /** Compiled matchers for every guarded selector, built from the config. */
    private List<GuardedSelector> guardedSelectors = new ArrayList<>();

    private long confirmationTimeoutMillis;
    private boolean allowBypassPermission;
    private boolean logToConsole;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        getServer().getPluginManager().registerEvents(new CommandGuardListener(this), this);

        ConfirmCommand confirmCommand = new ConfirmCommand(this);
        if (getCommand("aetp") != null) {
            getCommand("aetp").setExecutor(confirmCommand);
            getCommand("aetp").setTabCompleter(confirmCommand);
        }

        getLogger().info("Enabled. Guarding selectors: " + describeGuardedSelectors());
    }

    @Override
    public void onDisable() {
        pending.clear();
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

        long timeoutSeconds = getConfig().getLong("confirmation-timeout-seconds", 30L);
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30L;
        }
        this.confirmationTimeoutMillis = timeoutSeconds * 1000L;
        this.allowBypassPermission = getConfig().getBoolean("allow-bypass-permission", true);
        this.logToConsole = getConfig().getBoolean("log-to-console", true);
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

    // ---- Pending command storage ------------------------------------

    public void storePending(CommandSender sender, String commandWithoutSlash) {
        pending.put(senderKey(sender),
                new PendingCommand(commandWithoutSlash, System.currentTimeMillis() + confirmationTimeoutMillis));
    }

    /**
     * Removes and returns the sender's pending command, or {@code null} if there
     * is none or it has expired. Expired entries are cleared as a side effect.
     */
    public PendingCommand takePending(CommandSender sender) {
        PendingCommand entry = pending.remove(senderKey(sender));
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            return null;
        }
        return entry;
    }

    public boolean hasPending(CommandSender sender) {
        PendingCommand entry = pending.get(senderKey(sender));
        if (entry == null) {
            return false;
        }
        if (entry.isExpired()) {
            pending.remove(senderKey(sender));
            return false;
        }
        return true;
    }

    public void clearPending(CommandSender sender) {
        pending.remove(senderKey(sender));
    }

    /** A stable identity for a sender so console and each player get their own slot. */
    private String senderKey(CommandSender sender) {
        if (sender instanceof org.bukkit.entity.Player) {
            return "player:" + ((org.bukkit.entity.Player) sender).getUniqueId();
        }
        return "other:" + sender.getName();
    }

    // ---- Config accessors -------------------------------------------

    public boolean isBypassAllowed() {
        return allowBypassPermission;
    }

    public boolean isLogToConsole() {
        return logToConsole;
    }

    public long getConfirmationTimeoutSeconds() {
        return confirmationTimeoutMillis / 1000L;
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
