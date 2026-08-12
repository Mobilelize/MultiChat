package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzSound;
import net.mobilelize.fabriclib.registry.LzRegistry;
import net.mobilelize.fabriclib.text.LzMutableText;
import net.mobilelize.fabriclib.text.LzStyle;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MultiChatApi;
import net.mobilelize.multichat.client.config.MultiChatConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Whole-message keyword highlighting. On any keyword match, the entire message is rebuilt in two
 * spans — keyword and neutral — each with its own fully configurable style (color, bold, italic,
 * underline, strikethrough, obfuscated), replacing the message's original per-span formatting.
 * User keywords and API-registered keywords are independent sources with their own enable toggle,
 * but share the same keyword/neutral style pair.
 *
 * Every highlighted message also gets a hover tooltip (matched keywords + word count) and a click
 * action that reverts it back to its pre-highlight formatting — reuses the same
 * "/multichat execute <uuid>" + TAB_ACTION mechanism tab click-actions already use.
 */
public final class HighlightEngine {

    private HighlightEngine() {}

    // sourceModId -> keywords that mod registered
    private static final Map<String, List<String>> apiKeywords = new LinkedHashMap<>();

    // sourceModId -> exception words that mod registered (shields matches the same as the player's
    // own exception lists — see allExceptions)
    private static final Map<String, List<String>> apiExceptions = new LinkedHashMap<>();

    // actionId -> data needed to restore the message this actionId's revert click belongs to
    private static final Map<String, RevertData> pendingReverts = new HashMap<>();

    // touchedTabs is exactly the tabs that ended up storing this message — set once routing
    // finishes (see MessageRouter), so a revert click only ever has to scan those, not every tab.
    private record RevertData(LzChatEntry entry, LzText originalContent, List<ChatTab> touchedTabs) {}

    static {
        MultiChatApi.TAB_ACTION.register(HighlightEngine::onRevertAction);
    }

    /** Idempotent — safe to call repeatedly with the same pattern (e.g. mod re-init, hot reload). */
    public static void addApiRule(String pattern, String sourceModId) {
        if (pattern == null || pattern.isEmpty()) return;
        List<String> list = apiKeywords.computeIfAbsent(sourceModId, k -> new ArrayList<>());
        for (String existing : list) if (existing.equalsIgnoreCase(pattern)) return;
        list.add(pattern);
    }

    public static void removeApiRule(String pattern, String sourceModId) {
        List<String> list = apiKeywords.get(sourceModId);
        if (list != null) list.removeIf(p -> p.equalsIgnoreCase(pattern));
    }

    /** Idempotent — safe to call repeatedly with the same pattern (e.g. mod re-init, hot reload). */
    public static void addApiException(String pattern, String sourceModId) {
        if (pattern == null || pattern.isEmpty()) return;
        List<String> list = apiExceptions.computeIfAbsent(sourceModId, k -> new ArrayList<>());
        for (String existing : list) if (existing.equalsIgnoreCase(pattern)) return;
        list.add(pattern);
    }

    public static void removeApiException(String pattern, String sourceModId) {
        List<String> list = apiExceptions.get(sourceModId);
        if (list != null) list.removeIf(p -> p.equalsIgnoreCase(pattern));
    }

    /**
     * True only the very first time this modId has ever called this method, across every past
     * session — persisted to config, not just this runtime. Marks the modId as seen immediately,
     * so a mod can seed its default keyword list exactly once (on its actual first connect) and
     * never again, instead of blindly re-adding its full default list — and silently undoing any
     * word a user removed — on every single client start.
     */
    public static boolean isFirstRegistration(String sourceModId) {
        Set<String> seen = MultichatClient.CONFIG.registeredHighlightMods;
        if (seen.contains(sourceModId)) return false;
        seen.add(sourceModId);
        MultichatClient.saveTabsToConfig();
        return true;
    }

    private static List<String> allApiKeywords() {
        List<String> all = new ArrayList<>();
        for (List<String> list : apiKeywords.values()) all.addAll(list);
        return all;
    }

    /** Read-only snapshot of every keyword currently registered by other mods — for display only. */
    public static List<String> getApiKeywords() {
        return allApiKeywords();
    }

    private static List<String> allApiExceptions() {
        List<String> all = new ArrayList<>();
        for (List<String> list : apiExceptions.values()) all.addAll(list);
        return all;
    }

    /** Read-only snapshot of every exception word currently registered by other mods — for display only. */
    public static List<String> getApiExceptions() {
        return allApiExceptions();
    }

    public static Set<String> allSoundIds() {
        return LzRegistry.soundIds();
    }

    public static final class HighlightResult {
        public final LzText content;
        public final LzText raw;
        public final String actionId; // null if nothing matched — no revert to register
        HighlightResult(LzText content, LzText raw, String actionId) {
            this.content = content;
            this.raw = raw;
            this.actionId = actionId;
        }
    }

    /**
     * Matches against rawLzText (the canonical text used for routing/filter decisions elsewhere).
     * Returns content/raw unchanged (same references) and a null actionId if nothing matched.
     * Doesn't know which tabs will store this message yet (routing hasn't happened) — the caller
     * must follow up with {@link #registerRevert} once routing finishes.
     */
    public static HighlightResult apply(LzText content, LzText rawLzText, LzChatEntry entry) {
        MultiChatConfig cfg = MultichatClient.CONFIG;
        String plain = rawLzText.string();
        if (plain.isEmpty()) return new HighlightResult(content, rawLzText, null);
        String lower = plain.toLowerCase();
        List<String> allExceptions = allExceptions(cfg);
        List<int[]> protectedRanges = protectedRanges(lower, allExceptions);
        // A chat_heads (or similar mod) embedded object resolves through getString() to its own
        // junk fallback text (e.g. "[Bob head]") — never a real keyword to match against, so it's
        // protected the same as an exception word. Same ranges reformat() below uses to splice the
        // real object node back in instead of literal-copying that junk.
        for (LzText.ObjectSpan span : LzText.objectSpans(rawLzText.mc())) protectedRanges.add(new int[]{span.start(), span.end()});

        List<String> pool = null;
        if (cfg.userHighlightEnabled && matchesAny(lower, cfg.userHighlightKeywords, protectedRanges)) {
            pool = cfg.userHighlightKeywords;
        } else if (cfg.apiHighlightEnabled) {
            List<String> apiKw = allApiKeywords();
            if (matchesAny(lower, apiKw, protectedRanges)) pool = apiKw;
        }
        if (pool == null) return new HighlightResult(content, rawLzText, null);

        if (cfg.highlightSoundEnabled) {
            LzSound.playMaster(cfg.highlightSound, cfg.highlightSoundPitch, cfg.highlightSoundVolume);
        }

        List<String> matched = matchedKeywords(lower, pool, protectedRanges);

        String actionId = UUID.randomUUID().toString();

        LzText hoverText = buildHoverText(matched, cfg.keywordStyle.color);

        LzStyle keywordStyle = styleOf(cfg.keywordStyle)
            .withClickRunCommand("/multichat execute " + actionId)
            .withHoverText(hoverText);
        LzStyle neutralStyle = styleOf(cfg.neutralStyle)
            .withClickRunCommand("/multichat execute " + actionId)
            .withHoverText(hoverText);

        LzText newContent = reformat(content, pool, keywordStyle, neutralStyle, allExceptions);
        LzText newRaw     = reformat(rawLzText, pool, keywordStyle, neutralStyle, allExceptions);
        return new HighlightResult(newContent, newRaw, actionId);
    }

    /** Called by MessageRouter once routing finishes, with the exact tabs that ended up storing this message. */
    public static void registerRevert(String actionId, LzChatEntry entry, LzText originalContent, List<ChatTab> touchedTabs) {
        if (touchedTabs.isEmpty()) return; // message matched no tab (e.g. cancelled elsewhere) — nothing to revert
        pendingReverts.put(actionId, new RevertData(entry, originalContent, touchedTabs));
    }

    private static void onRevertAction(String actionId) {
        RevertData data = pendingReverts.remove(actionId);
        if (data == null) return;

        for (ChatTab tab : data.touchedTabs()) {
            List<ChatTab.MessageEntry> msgs = tab.messages();
            for (int i = 0; i < msgs.size(); i++) {
                if (msgs.get(i).entry() != data.entry()) continue;
                LzText base = (tab.type() == TabType.ALL) ? data.originalContent() : data.entry().originalContent;
                LzText rendered = tab.showTimestamps()
                    ? ChatManager.stampedText(data.entry().creationTick, base, data.entry().indicator)
                    : base;
                msgs.set(i, new ChatTab.MessageEntry(rendered, data.originalContent(), data.entry()));
                break; // identity is unique within a tab's message list — no need to keep scanning
            }
        }

        for (ChatPanel panel : ChatManager.getAllActivePanels())
            for (ChatTab tab : data.touchedTabs())
                panel.rebuildTabLines(tab);
    }

    /** Your Exceptions, API Exceptions, and mod-registered exceptions are separate lists purely for
     *  organization — all of them protect against a match from either keyword pool, so they're merged
     *  before any matching happens. */
    private static List<String> allExceptions(MultiChatConfig cfg) {
        List<String> all = new ArrayList<>();
        if (cfg.exceptionsEnabled) all.addAll(cfg.highlightExceptions);
        if (cfg.apiExceptionsEnabled) {
            all.addAll(cfg.apiHighlightExceptions);
            all.addAll(allApiExceptions());
        }
        return all;
    }

    /** Every occurrence range of every exception word in lowerPlain — keyword matches overlapping
     *  one of these are treated as not-matched, without touching any other keyword hit in the message. */
    private static List<int[]> protectedRanges(String lowerPlain, List<String> exceptions) {
        List<int[]> ranges = new ArrayList<>();
        if (exceptions == null) return ranges;
        for (String ex : exceptions) {
            if (ex == null || ex.isEmpty()) continue;
            String exLower = ex.toLowerCase();
            int from = 0, idx;
            while ((idx = lowerPlain.indexOf(exLower, from)) >= 0) {
                ranges.add(new int[]{idx, idx + exLower.length()});
                from = idx + 1;
            }
        }
        return ranges;
    }

    private static boolean overlapsProtected(int start, int end, List<int[]> protectedRanges) {
        for (int[] r : protectedRanges) {
            if (start < r[1] && end > r[0]) return true;
        }
        return false;
    }

    private static boolean matchesAny(String lowerPlain, List<String> keywords, List<int[]> protectedRanges) {
        for (String kw : keywords) {
            if (kw.isEmpty()) continue;
            String kwLower = kw.toLowerCase();
            int from = 0, idx;
            while ((idx = lowerPlain.indexOf(kwLower, from)) >= 0) {
                if (!overlapsProtected(idx, idx + kwLower.length(), protectedRanges)) return true;
                from = idx + 1;
            }
        }
        return false;
    }

    private static List<String> matchedKeywords(String lowerPlain, List<String> keywords, List<int[]> protectedRanges) {
        List<String> found = new ArrayList<>();
        for (String kw : keywords) {
            if (kw.isEmpty() || found.contains(kw)) continue;
            String kwLower = kw.toLowerCase();
            int from = 0, idx;
            while ((idx = lowerPlain.indexOf(kwLower, from)) >= 0) {
                if (!overlapsProtected(idx, idx + kwLower.length(), protectedRanges)) { found.add(kw); break; }
                from = idx + 1;
            }
        }
        return found;
    }

    private static LzText buildHoverText(List<String> matched, int keywordColor) {
        LzStyle labelStyle   = LzStyle.ofColor(Colors.TEXT_DIMMED & 0xFFFFFF);
        LzStyle keywordStyle = LzStyle.ofColor(keywordColor).withBold(true);
        LzStyle hintStyle    = LzStyle.ofColor(Colors.TEXT_MID_GRAY & 0xFFFFFF).withItalic(true);

        LzMutableText text = LzText.literal("Keywords: ").withStyle(labelStyle);
        text.append(LzText.literal(String.join(", ", matched)).withStyle(keywordStyle));
        text.append(LzText.literal("\nClick to revert formatting").withStyle(hintStyle));
        return text;
    }

    private static LzStyle styleOf(MultiChatConfig.HighlightStyle s) {
        return LzStyle.ofColor(s.color)
            .withBold(s.bold)
            .withItalic(s.italic)
            .withUnderline(s.underline)
            .withStrikethrough(s.strikethrough)
            .withObfuscated(s.obfuscated);
    }

    private static LzMutableText reformat(LzText source, List<String> keywords, LzStyle keywordStyle, LzStyle neutralStyle, List<String> exceptions) {
        String plain = source.string();
        List<int[]> protectedRanges = protectedRanges(plain.toLowerCase(), exceptions);
        // objectSpans are already excluded from ever being a keyword match (apply() adds them to
        // its own protectedRanges before matching) — this list is only consulted here, to splice
        // the real node back in wherever its junk fallback text would otherwise get flattened into
        // literal characters by the plain-string rebuild below.
        List<LzText.ObjectSpan> objectSpans = LzText.objectSpans(source.mc());
        LzMutableText result = LzText.empty();
        int i = 0;
        while (i < plain.length()) {
            LzText.ObjectSpan nextObj = null;
            for (LzText.ObjectSpan span : objectSpans) {
                if (span.start() >= i) { nextObj = span; break; }
            }
            int[] match = findEarliestMatch(plain, i, keywords, protectedRanges);

            int objStart   = nextObj != null ? nextObj.start() : Integer.MAX_VALUE;
            int matchStart = match   != null ? match[0]        : Integer.MAX_VALUE;

            if (objStart <= matchStart) {
                if (objStart > i) result.append(LzText.literal(plain.substring(i, objStart)).withStyle(neutralStyle));
                result.append(LzText.wrapMc(nextObj.node())); // real node, own style untouched — not recolored
                i = nextObj.end();
                continue;
            }
            if (match == null) {
                result.append(LzText.literal(plain.substring(i)).withStyle(neutralStyle));
                break;
            }
            int start = match[0], end = match[1];
            if (start > i) result.append(LzText.literal(plain.substring(i, start)).withStyle(neutralStyle));
            result.append(LzText.literal(plain.substring(start, end)).withStyle(keywordStyle));
            i = end;
        }
        return result;
    }

    private static int[] findEarliestMatch(String plain, int fromIdx, List<String> keywords, List<int[]> protectedRanges) {
        String lower = plain.toLowerCase();
        int bestStart = -1, bestEnd = -1;
        for (String kw : keywords) {
            if (kw.isEmpty()) continue;
            String kwLower = kw.toLowerCase();
            int idx = fromIdx;
            while ((idx = lower.indexOf(kwLower, idx)) >= 0 && overlapsProtected(idx, idx + kwLower.length(), protectedRanges)) idx++;
            if (idx >= 0 && (bestStart < 0 || idx < bestStart)) {
                bestStart = idx;
                bestEnd = idx + kwLower.length();
            }
        }
        return bestStart < 0 ? null : new int[]{bestStart, bestEnd};
    }
}
