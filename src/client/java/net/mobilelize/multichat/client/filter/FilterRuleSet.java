package net.mobilelize.multichat.client.filter;

import net.minecraft.text.Text;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzText;

import java.util.ArrayList;
import java.util.List;

public class FilterRuleSet {

    private final List<FilterRule> whitelist = new ArrayList<>();
    private final List<FilterRule> blacklist = new ArrayList<>();
    private WhitelistMode whitelistMode = WhitelistMode.ANY;
    private BlacklistMode blacklistMode = BlacklistMode.ANY;

    public List<FilterRule>  whitelist()                        { return whitelist; }
    public List<FilterRule>  blacklist()                        { return blacklist; }
    public WhitelistMode     whitelistMode()                    { return whitelistMode; }
    public void              setWhitelistMode(WhitelistMode v)  { whitelistMode = v; }
    public BlacklistMode     blacklistMode()                    { return blacklistMode; }
    public void              setBlacklistMode(BlacklistMode v)  { blacklistMode = v; }

    public boolean matches(LzChatLine text) {
        final LzText message = new LzText((Text) text.content().mc());
        return matches(message.mc().getString(), text.indicator());
    }

    public boolean matches(String text, LzMessageIndicator indicator) {
        if (!blacklist.isEmpty()) {
            if (blacklistMode == BlacklistMode.ALL) {
                boolean allMatch = true;
                for (FilterRule rule : blacklist) {
                    if (!rule.matches(text, indicator)) { allMatch = false; break; }
                }
                if (allMatch) return false;
            } else {
                for (FilterRule rule : blacklist) {
                    if (rule.matches(text, indicator)) return false;
                }
            }
        }
        if (whitelist.isEmpty()) return true;
        if (whitelistMode == WhitelistMode.ALL) {
            for (FilterRule rule : whitelist) {
                if (!rule.matches(text, indicator)) return false;
            }
            return true;
        }
        for (FilterRule rule : whitelist) {
            if (rule.matches(text, indicator)) return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return whitelist.isEmpty() && blacklist.isEmpty();
    }

    /** Immutable point-in-time copy — {@link #whitelist()}/{@link #blacklist()} return the live,
     *  unsynchronized lists a screen's Save button mutates in place (clear() + repeated add()); a
     *  background thread calling {@link #matches} against those live lists while a save is
     *  concurrently touching them races on the same ArrayLists with no synchronization at all, which
     *  can corrupt the iteration rather than just seeing a slightly-stale value. Anything doing
     *  matching off the main thread (see ChatManager's FilterCoordinator use) must call this first,
     *  on the main thread, and match against the returned snapshot instead of this FilterRuleSet
     *  directly. */
    public Snapshot snapshot() {
        return new Snapshot(List.copyOf(whitelist), whitelistMode, List.copyOf(blacklist), blacklistMode);
    }

    /** Same matching logic as {@link #matches(String, LzMessageIndicator)}, over copied lists —
     *  {@link FilterRule} is an immutable record and {@code List.copyOf} is itself immutable, so this
     *  is safe to share with and call from any thread once created. */
    public record Snapshot(List<FilterRule> whitelist, WhitelistMode whitelistMode,
                            List<FilterRule> blacklist, BlacklistMode blacklistMode) {

        public boolean matches(String text, LzMessageIndicator indicator) {
            if (!blacklist.isEmpty()) {
                if (blacklistMode == BlacklistMode.ALL) {
                    boolean allMatch = true;
                    for (FilterRule rule : blacklist) {
                        if (!rule.matches(text, indicator)) { allMatch = false; break; }
                    }
                    if (allMatch) return false;
                } else {
                    for (FilterRule rule : blacklist) {
                        if (rule.matches(text, indicator)) return false;
                    }
                }
            }
            if (whitelist.isEmpty()) return true;
            if (whitelistMode == WhitelistMode.ALL) {
                for (FilterRule rule : whitelist) {
                    if (!rule.matches(text, indicator)) return false;
                }
                return true;
            }
            for (FilterRule rule : whitelist) {
                if (rule.matches(text, indicator)) return true;
            }
            return false;
        }
    }
}
