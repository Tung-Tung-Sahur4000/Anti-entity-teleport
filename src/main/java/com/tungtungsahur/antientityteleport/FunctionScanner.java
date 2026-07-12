package com.tungtungsahur.antientityteleport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads datapack {@code .mcfunction} files so a {@code /function} invocation can
 * be checked before it runs.
 *
 * <p>A function is a <em>chain</em> of commands, and it may call other functions
 * (or function tags). This scanner walks that whole chain, following nested
 * {@code function} / {@code execute ... run function} calls and {@code #tags},
 * and reports the first guarded selector it finds anywhere in the chain.</p>
 */
public final class FunctionScanner {

    /** Finds a {@code function <id>} reference anywhere in a command line. */
    private static final Pattern FUNCTION_REF =
            Pattern.compile("(?:^|\\s)function\\s+(#?[a-zA-Z0-9_.\\-]*:?[a-zA-Z0-9_./\\-]+)");

    private static final int MAX_DEPTH = 50;

    private final AntiEntityTeleport plugin;

    public FunctionScanner(AntiEntityTeleport plugin) {
        this.plugin = plugin;
    }

    /** The outcome of scanning a function chain. */
    public static final class ScanResult {
        /** The guarded selector found, or {@code null} if none was found. */
        public final AntiEntityTeleport.GuardedSelector match;
        /** The function id (namespace:path) the selector was found in. */
        public final String matchedFunction;
        /** True if some referenced function/tag could not be read and verified. */
        public final boolean unreadable;

        private ScanResult(AntiEntityTeleport.GuardedSelector match, String matchedFunction, boolean unreadable) {
            this.match = match;
            this.matchedFunction = matchedFunction;
            this.unreadable = unreadable;
        }

        static ScanResult none() {
            return new ScanResult(null, null, false);
        }

        static ScanResult unreadable() {
            return new ScanResult(null, null, true);
        }

        static ScanResult found(AntiEntityTeleport.GuardedSelector match, String function) {
            return new ScanResult(match, function, false);
        }

        public boolean hasMatch() {
            return match != null;
        }
    }

    /** Pulls every {@code function <id>} reference out of a single command line. */
    public static List<String> extractFunctionIds(String command) {
        List<String> ids = new ArrayList<>();
        if (command == null || command.isEmpty()) {
            return ids;
        }
        Matcher m = FUNCTION_REF.matcher(command);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    /** Scans the full chain reachable from {@code functionId}. Never throws. */
    public ScanResult scanChain(String functionId) {
        try {
            return scanInternal(functionId, new HashSet<>(), 0);
        } catch (Throwable t) {
            // Something unexpected (odd datapack layout, IO, JSON) -> report as
            // "unreadable" so the caller can decide, rather than crashing a command.
            plugin.getLogger().warning("Could not fully scan function " + functionId + ": " + t.getMessage());
            return ScanResult.unreadable();
        }
    }

    private ScanResult scanInternal(String rawId, Set<String> visited, int depth) {
        if (depth > MAX_DEPTH) {
            return ScanResult.none();
        }

        String id = normalizeId(rawId);
        if (!visited.add(id)) {
            return ScanResult.none(); // already visited -> cycle guard
        }

        if (id.startsWith("#")) {
            return scanTag(id, visited, depth);
        }

        File file = locateFunctionFile(id);
        if (file == null) {
            return ScanResult.unreadable();
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ScanResult.unreadable();
        }

        boolean sawUnreadable = false;
        for (String rawLine : lines) {
            String line = cleanLine(rawLine);
            if (line.isEmpty()) {
                continue;
            }

            AntiEntityTeleport.GuardedSelector selector = plugin.findGuardedSelector(line);
            if (selector != null) {
                return ScanResult.found(selector, id);
            }

            for (String nested : extractFunctionIds(line)) {
                ScanResult sub = scanInternal(nested, visited, depth + 1);
                if (sub.hasMatch()) {
                    return sub;
                }
                if (sub.unreadable) {
                    sawUnreadable = true;
                }
            }
        }
        return sawUnreadable ? ScanResult.unreadable() : ScanResult.none();
    }

    /** Resolves a {@code #namespace:tag} into its member functions and scans each. */
    private ScanResult scanTag(String tagId, Set<String> visited, int depth) {
        String bare = tagId.substring(1); // drop '#'
        File tagFile = locateTagFile(normalizeId(bare));
        if (tagFile == null) {
            return ScanResult.unreadable();
        }

        List<String> members;
        try {
            String content = new String(Files.readAllBytes(tagFile.toPath()), StandardCharsets.UTF_8);
            members = readTagValues(content);
        } catch (Exception e) {
            return ScanResult.unreadable();
        }
        if (members == null) {
            return ScanResult.unreadable();
        }

        boolean sawUnreadable = false;
        for (String member : members) {
            ScanResult sub = scanInternal(member, visited, depth + 1);
            if (sub.hasMatch()) {
                return sub;
            }
            if (sub.unreadable) {
                sawUnreadable = true;
            }
        }
        return sawUnreadable ? ScanResult.unreadable() : ScanResult.none();
    }

    private List<String> readTagValues(String json) {
        List<String> ids = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return ids;
        }
        JsonObject obj = root.getAsJsonObject();
        if (!obj.has("values") || !obj.get("values").isJsonArray()) {
            return ids;
        }
        JsonArray values = obj.getAsJsonArray("values");
        for (JsonElement element : values) {
            if (element.isJsonPrimitive()) {
                ids.add(element.getAsString());
            } else if (element.isJsonObject() && element.getAsJsonObject().has("id")) {
                ids.add(element.getAsJsonObject().get("id").getAsString());
            }
        }
        return ids;
    }

    // ---- File location ----------------------------------------------

    /** Finds the .mcfunction file for {@code namespace:path} in any world datapack. */
    private File locateFunctionFile(String id) {
        String[] parts = splitId(id);
        String namespace = parts[0];
        String path = parts[1];
        for (File datapacks : datapackDirs()) {
            for (File pack : safeListDirs(datapacks)) {
                File dataNs = new File(pack, "data" + File.separator + namespace);
                // "function" (1.21+) and "functions" (pre-1.21) directory names.
                File candidate = new File(dataNs, "function" + File.separator + path + ".mcfunction");
                if (candidate.isFile()) {
                    return candidate;
                }
                candidate = new File(dataNs, "functions" + File.separator + path + ".mcfunction");
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private File locateTagFile(String id) {
        String[] parts = splitId(id);
        String namespace = parts[0];
        String path = parts[1];
        for (File datapacks : datapackDirs()) {
            for (File pack : safeListDirs(datapacks)) {
                File dataNs = new File(pack, "data" + File.separator + namespace);
                File candidate = new File(dataNs,
                        "tags" + File.separator + "function" + File.separator + path + ".json");
                if (candidate.isFile()) {
                    return candidate;
                }
                candidate = new File(dataNs,
                        "tags" + File.separator + "functions" + File.separator + path + ".json");
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Every world's {@code datapacks} folder (usually only the main world has one). */
    private List<File> datapackDirs() {
        List<File> dirs = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            File dir = new File(world.getWorldFolder(), "datapacks");
            if (dir.isDirectory()) {
                dirs.add(dir);
            }
        }
        return dirs;
    }

    private File[] safeListDirs(File parent) {
        File[] children = parent.listFiles(File::isDirectory);
        return children == null ? new File[0] : children;
    }

    // ---- Small helpers ----------------------------------------------

    /** Ensures an id carries a namespace, defaulting to {@code minecraft}. */
    private String normalizeId(String id) {
        String value = id.trim();
        boolean tag = value.startsWith("#");
        String body = tag ? value.substring(1) : value;
        if (!body.contains(":")) {
            body = "minecraft:" + body;
        }
        return tag ? "#" + body : body;
    }

    private String[] splitId(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return new String[]{"minecraft", id};
        }
        return new String[]{id.substring(0, colon), id.substring(colon + 1)};
    }

    /** Strips a trailing/whole-line comment and surrounding whitespace. */
    private String cleanLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return "";
        }
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }
}
