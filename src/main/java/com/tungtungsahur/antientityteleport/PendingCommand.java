package com.tungtungsahur.antientityteleport;

/**
 * A command that was held back for confirmation, together with the moment it
 * stops being valid.
 */
public final class PendingCommand {

    /** The command to run WITHOUT a leading slash. */
    private final String command;
    private final long expiresAtMillis;

    public PendingCommand(String command, long expiresAtMillis) {
        this.command = command;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getCommand() {
        return command;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }
}
