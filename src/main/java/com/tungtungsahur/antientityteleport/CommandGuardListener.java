package com.tungtungsahur.antientityteleport;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.List;

/**
 * Intercepts vanilla commands from players and the console before they run.
 *
 * <p>A command is blocked when:</p>
 * <ul>
 *   <li>it literally contains a guarded selector (e.g. {@code /kill @e}), or</li>
 *   <li>it runs a {@code function} whose command chain contains one
 *       (e.g. {@code /function my:cleanup} where the file kills {@code @e}).</li>
 * </ul>
 *
 * <p>To run a blocked command, re-type it with the confirmation keyword on the
 * end: {@code /tp @e noobgamer23 confirm} or {@code /function my:cleanup confirm}.</p>
 *
 * <p>Note: command blocks and commands executed from inside a function do not
 * fire Bukkit events, so they cannot be intercepted individually. Functions are
 * instead checked at their {@code /function} entry point by reading the file.</p>
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
        // A command block cannot type "confirm", so blocking it would break the
        // contraption forever. Leave command blocks alone unless explicitly asked
        // to guard them. (This handler otherwise covers the console.)
        if (event.getSender() instanceof BlockCommandSender && !plugin.isGuardCommandBlocks()) {
            return;
        }
        String withoutSlash = stripLeadingSlash(event.getCommand());
        if (handle(event.getSender(), withoutSlash)) {
            event.setCancelled(true);
        }
    }

    /**
     * Core logic, shared by player and console.
     *
     * @return {@code true} if the original event must be cancelled (the command
     *         was blocked, or it was the confirming variant we run ourselves
     *         after stripping the keyword); {@code false} to let it proceed.
     */
    private boolean handle(CommandSender sender, String raw) {
        if (isBypassing(sender)) {
            return false; // trusted sender, run immediately
        }

        if (plugin.hasConfirmationSuffix(raw)) {
            String core = plugin.stripConfirmationSuffix(raw);
            Guard guard = evaluate(core);
            if (guard.dangerous()) {
                // Confirmed -> run the real command directly. dispatchCommand does
                // NOT re-fire these events, so it cannot loop back into the guard.
                sender.sendMessage(plugin.prefix() + plugin.message("confirmed"));
                if (plugin.isLogToConsole()) {
                    plugin.getLogger().info(sender.getName() + " confirmed guarded command: /" + core);
                }
                Bukkit.dispatchCommand(sender, core);
                return true; // cancel the raw "... confirm" so it doesn't error out
            }
            // The trailing "confirm" wasn't confirming anything guarded. Only
            // block if the raw command itself is dangerous; otherwise let it run.
            Guard rawGuard = evaluate(raw);
            if (rawGuard.dangerous()) {
                warn(sender, raw, rawGuard);
                return true;
            }
            return false;
        }

        Guard guard = evaluate(raw);
        if (guard.dangerous()) {
            warn(sender, raw, guard);
            return true;
        }
        return false;
    }

    /** Works out whether a command is dangerous and why (literal selector or function). */
    private Guard evaluate(String command) {
        AntiEntityTeleport.GuardedSelector literal = plugin.findGuardedSelector(command);
        if (literal != null) {
            return Guard.selector(literal);
        }

        if (plugin.isScanFunctions()) {
            List<String> functionIds = FunctionScanner.extractFunctionIds(command);
            for (String functionId : functionIds) {
                FunctionScanner.ScanResult result = plugin.getFunctionScanner().scanChain(functionId);
                if (result.hasMatch()) {
                    return Guard.function(functionId, result.matchedFunction, result.match);
                }
                if (result.unreadable && plugin.isConfirmUnreadableFunctions()) {
                    return Guard.unreadableFunction(functionId);
                }
            }
        }
        return Guard.safe();
    }

    /** Warns the sender and tells them to re-type the command with the keyword. */
    private void warn(CommandSender sender, String core, Guard guard) {
        if (plugin.isLogToConsole()) {
            plugin.getLogger().warning(sender.getName() + " tried to run a guarded command ("
                    + guard.describe() + "): /" + core + " -> blocked, awaiting confirmation.");
        }

        sender.sendMessage(plugin.prefix() + guard.warningLine(plugin));
        sender.sendMessage(plugin.prefix()
                + plugin.message("how-to-confirm",
                "{keyword}", plugin.getConfirmationKeyword(),
                "{command}", "/" + core + " " + plugin.getConfirmationKeyword()));

        if (sender instanceof Player) {
            sendClickableConfirm((Player) sender, core);
        }
    }

    private void sendClickableConfirm(Player player, String core) {
        String confirmCommand = "/" + core + " " + plugin.getConfirmationKeyword();

        TextComponent component = new TextComponent(
                new ComponentBuilder(plugin.prefix()).create());
        TextComponent button = new TextComponent(
                new ComponentBuilder(plugin.message("click-to-confirm")).create());
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

    /** Why a command is (or isn't) dangerous, plus how to describe it to the user. */
    private static final class Guard {
        private enum Kind { SAFE, SELECTOR, FUNCTION, UNREADABLE_FUNCTION }

        private final Kind kind;
        private final AntiEntityTeleport.GuardedSelector selector;
        private final String invokedFunction;
        private final String matchedFunction;

        private Guard(Kind kind, AntiEntityTeleport.GuardedSelector selector,
                      String invokedFunction, String matchedFunction) {
            this.kind = kind;
            this.selector = selector;
            this.invokedFunction = invokedFunction;
            this.matchedFunction = matchedFunction;
        }

        static Guard safe() {
            return new Guard(Kind.SAFE, null, null, null);
        }

        static Guard selector(AntiEntityTeleport.GuardedSelector selector) {
            return new Guard(Kind.SELECTOR, selector, null, null);
        }

        static Guard function(String invoked, String matched, AntiEntityTeleport.GuardedSelector selector) {
            return new Guard(Kind.FUNCTION, selector, invoked, matched);
        }

        static Guard unreadableFunction(String invoked) {
            return new Guard(Kind.UNREADABLE_FUNCTION, null, invoked, null);
        }

        boolean dangerous() {
            return kind != Kind.SAFE;
        }

        String describe() {
            switch (kind) {
                case SELECTOR:
                    return selector.raw();
                case FUNCTION:
                    return selector.raw() + " in function " + matchedFunction;
                case UNREADABLE_FUNCTION:
                    return "unverifiable function " + invokedFunction;
                default:
                    return "safe";
            }
        }

        String warningLine(AntiEntityTeleport plugin) {
            switch (kind) {
                case FUNCTION:
                    return plugin.message("warning-function",
                            "{function}", invokedFunction,
                            "{inner}", matchedFunction,
                            "{selector}", selector.raw());
                case UNREADABLE_FUNCTION:
                    return plugin.message("warning-unreadable-function",
                            "{function}", invokedFunction);
                case SELECTOR:
                default:
                    return plugin.message("warning", "{selector}", selector.raw());
            }
        }
    }
}
