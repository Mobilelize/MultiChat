package net.mobilelize.multichat.client.filter;

import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.mobilelize.fabriclib.client.LzMessageIndicator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public record FilterRule (FilterType type, String value, IndicatorFilter indicator) {

    // matches() runs per rule, per tab, per incoming message — live, on the main thread, for every
    // single chat message — and again from FilterCoordinator's background thread during a batch
    // refilter. Recompiling a Pattern from scratch every call (as this used to do) means a burst of
    // messages pays for a fresh regex compile per rule per message, which is expensive enough to
    // freeze a frame. Cache compiled patterns keyed by the exact regex actually used, shared across
    // both threads via ConcurrentHashMap. A rule's (type, value) combo — and therefore the regex it
    // produces — never changes after construction (FilterRule is immutable), so nothing ever needs
    // to be invalidated; the cache just fills in once per distinct value a user has ever configured.
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
    // A pattern that can never match anything — cached in place of a genuinely invalid user regex, so
    // a bad pattern fails to compile (and gets treated as "never matches", same as before) exactly
    // once instead of re-attempting (and re-throwing) the same failing compile on every message.
    private static final Pattern NEVER_MATCHES = Pattern.compile("(?!)");

    private static Pattern cachedPattern(String key, String regex) {
        return cachedPattern(key, regex, 0);
    }

    private static Pattern cachedPattern(String key, String regex, int flags) {
        return PATTERN_CACHE.computeIfAbsent(key, k -> {
            try { return Pattern.compile(regex, flags); } catch (Exception e) { return NEVER_MATCHES; }
        });
    }

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
            case WORD        -> cachedPattern("W " + value,
                    "\\b" + Pattern.quote(value) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
            case FUZZY       -> fuzzyMatches(lower, val);
            // REGEX and REGEX_START compile the exact same pattern (find() vs. lookingAt(), which is
            // just an implicitly-start-anchored find()) — share one cache entry between them.
            case REGEX       -> cachedPattern("R " + value, value).matcher(text).find();
            case REGEX_START -> cachedPattern("R " + value, value).matcher(text).lookingAt();
            case REGEX_END   -> cachedPattern("E " + value, value + "\\z").matcher(text).find();
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
        final GuiMessageTag raw = ind != null ? ind.mc() : null;
        return switch (indicator) {
            case ANY        -> true;
            case PLAYER     -> raw == null;
            case SYSTEM     -> raw == GuiMessageTag.system() || raw == GuiMessageTag.systemSinglePlayer();
            case NOT_SECURE -> raw == GuiMessageTag.chatNotSecure();
            case MODIFIED   -> raw != null && raw.icon() == GuiMessageTag.Icon.CHAT_MODIFIED;
            case CHAT_ERROR -> raw == GuiMessageTag.chatError();
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
