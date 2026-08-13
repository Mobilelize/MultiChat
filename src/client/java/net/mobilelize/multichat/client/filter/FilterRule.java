package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.MessageIndicator;
import net.mobilelize.fabriclib.client.LzMessageIndicator;

import java.util.regex.Pattern;

public record FilterRule (FilterType type, String value, IndicatorFilter indicator) {

    public FilterRule(FilterType type, String value) {
        this(type, value, IndicatorFilter.ANY);
    }

    public boolean matches(String text, LzMessageIndicator ind) {
        if (!indicatorMatches(ind)) return false;

        final String lower = text.toLowerCase();
        final String val   = value.toLowerCase();
        return switch (type) {
            case CONTAINS    -> lower.contains(val);
            case STARTS_WITH -> lower.startsWith(val);
            case ENDS_WITH   -> lower.endsWith(val);
            case EXACT       -> lower.equals(val);
            case WORD        -> Pattern.compile(
                    "\\b" + Pattern.quote(value) + "\\b",
                    Pattern.CASE_INSENSITIVE).matcher(text).find();
            case FUZZY       -> fuzzyMatches(lower, val);
            case REGEX       -> { try { yield Pattern.compile(value).matcher(text).find(); } catch (Exception e) { yield false; } }
            case REGEX_START -> { try { yield Pattern.compile(value).matcher(text).lookingAt(); } catch (Exception e) { yield false; } }
            case REGEX_END   -> { try { yield Pattern.compile(value + "\\z").matcher(text).find(); } catch (Exception e) { yield false; } }
        };
    }

    private static boolean fuzzyMatches(String text, String query) {
        if (query.isEmpty()) return true;
        int qi = 0;
        for (int ti = 0; ti < text.length() && qi < query.length(); ti++)
            if (text.charAt(ti) == query.charAt(qi)) qi++;
        return qi == query.length();
    }

    private boolean indicatorMatches(LzMessageIndicator ind) {
        final MessageIndicator raw = ind != null ? ind.mc() : null;
        return switch (indicator) {
            case ANY        -> true;
            case PLAYER     -> raw == null;
            case SYSTEM     -> raw == MessageIndicator.system() || raw == MessageIndicator.singlePlayer();
            case NOT_SECURE -> raw == MessageIndicator.notSecure();
            case MODIFIED   -> raw != null && raw.icon() == MessageIndicator.Icon.CHAT_MODIFIED;
            case CHAT_ERROR -> raw == MessageIndicator.chatError();
        };
    }

    public static String normalizeFilterText(String text) {
        return text
                .replace("￼", "")
                .replaceAll("\\p{C}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
