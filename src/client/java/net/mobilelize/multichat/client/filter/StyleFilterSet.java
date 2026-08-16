package net.mobilelize.multichat.client.filter;

import net.mobilelize.fabriclib.text.LzText;

import java.util.ArrayList;
import java.util.List;

/**
 * A tab-level set of style conditions, separate from FilterRuleSet (which only ever sees plain text).
 * Empty = no restriction. When a tab has conditions, a message must also satisfy this set (in addition to
 * the tab's normal whitelist/blacklist) to be routed there — e.g. requiring a sender-name prefix to
 * actually render italic before treating it as genuine staff chat, which plain-text FilterRule can't check.
 *
 * Structured as its own whitelist/blacklist (mirrors FilterRuleSet's shape exactly, same matches() logic)
 * rather than a single list — same familiar mental model as the text-based rules.
 */
public class StyleFilterSet {

    private final List<StyleCondition> whitelist = new ArrayList<>();
    private final List<StyleCondition> blacklist = new ArrayList<>();
    private WhitelistMode whitelistMode = WhitelistMode.ANY;
    private BlacklistMode blacklistMode = BlacklistMode.ANY;

    public List<StyleCondition> whitelist()                       { return whitelist; }
    public List<StyleCondition> blacklist()                       { return blacklist; }
    public WhitelistMode        whitelistMode()                   { return whitelistMode; }
    public void                 setWhitelistMode(WhitelistMode v) { whitelistMode = v; }
    public BlacklistMode        blacklistMode()                   { return blacklistMode; }
    public void                 setBlacklistMode(BlacklistMode v) { blacklistMode = v; }

    public boolean isEmpty() { return whitelist.isEmpty() && blacklist.isEmpty(); }

    /** Immutable point-in-time copy — same reasoning as {@link FilterRuleSet#snapshot()}: whitelist()/
     *  blacklist() return the live, unsynchronized lists a screen's Save button mutates in place, so
     *  anything matching off the main thread must call this first and match against the snapshot. */
    public Snapshot snapshot() {
        return new Snapshot(List.copyOf(whitelist), whitelistMode, List.copyOf(blacklist), blacklistMode);
    }

    /** Same matching logic as {@link #matches(LzText)}, over copied lists — {@link StyleCondition} is
     *  an immutable record and {@code List.copyOf} is itself immutable, so this is safe to share with
     *  and call from any thread once created. */
    public record Snapshot(List<StyleCondition> whitelist, WhitelistMode whitelistMode,
                            List<StyleCondition> blacklist, BlacklistMode blacklistMode) {
        public boolean matches(LzText text) {
            if (!blacklist.isEmpty()) {
                if (blacklistMode == BlacklistMode.ALL) {
                    boolean allMatch = true;
                    for (StyleCondition c : blacklist) {
                        if (!c.matches(text)) { allMatch = false; break; }
                    }
                    if (allMatch) return false;
                } else {
                    for (StyleCondition c : blacklist) {
                        if (c.matches(text)) return false;
                    }
                }
            }
            if (whitelist.isEmpty()) return true;
            if (whitelistMode == WhitelistMode.ALL) {
                for (StyleCondition c : whitelist) {
                    if (!c.matches(text)) return false;
                }
                return true;
            }
            for (StyleCondition c : whitelist) {
                if (c.matches(text)) return true;
            }
            return false;
        }
    }

    public boolean matches(LzText text) {
        if (!blacklist.isEmpty()) {
            if (blacklistMode == BlacklistMode.ALL) {
                boolean allMatch = true;
                for (StyleCondition c : blacklist) {
                    if (!c.matches(text)) { allMatch = false; break; }
                }
                if (allMatch) return false;
            } else {
                for (StyleCondition c : blacklist) {
                    if (c.matches(text)) return false;
                }
            }
        }
        if (whitelist.isEmpty()) return true;
        if (whitelistMode == WhitelistMode.ALL) {
            for (StyleCondition c : whitelist) {
                if (!c.matches(text)) return false;
            }
            return true;
        }
        for (StyleCondition c : whitelist) {
            if (c.matches(text)) return true;
        }
        return false;
    }
}
