package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.MessageIndicator;

import java.util.ArrayList;
import java.util.List;

public class FilterRuleSet {

    public final List<FilterRule> whitelist = new ArrayList<>();
    public final List<FilterRule> blacklist = new ArrayList<>();
    public WhitelistMode whitelistMode  = WhitelistMode.ANY;
    public WhitelistMode blacklistMode  = WhitelistMode.ANY;

    public boolean matches(String text, MessageIndicator indicator) {
        if (!blacklist.isEmpty()) {
            if (blacklistMode == WhitelistMode.ALL) {
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
}
