package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChatEntry;
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

        List<String> pool = null;
        if (cfg.userHighlightEnabled && matchesAny(lower, cfg.userHighlightKeywords)) {
            pool = cfg.userHighlightKeywords;
        } else if (cfg.apiHighlightEnabled) {
            List<String> apiKw = allApiKeywords();
            if (matchesAny(lower, apiKw)) pool = apiKw;
        }
        if (pool == null) return new HighlightResult(content, rawLzText, null);

        List<String> matched = matchedKeywords(lower, pool);

        String actionId = UUID.randomUUID().toString();

        LzText hoverText = buildHoverText(matched, cfg.keywordStyle.color);

        LzStyle keywordStyle = styleOf(cfg.keywordStyle)
            .withClickRunCommand("/multichat execute " + actionId)
            .withHoverText(hoverText);
        LzStyle neutralStyle = styleOf(cfg.neutralStyle)
            .withClickRunCommand("/multichat execute " + actionId)
            .withHoverText(hoverText);

        LzText newContent = reformat(content.string(), pool, keywordStyle, neutralStyle);
        LzText newRaw     = reformat(rawLzText.string(), pool, keywordStyle, neutralStyle);
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

    private static boolean matchesAny(String lowerPlain, List<String> keywords) {
        for (String kw : keywords) {
            if (!kw.isEmpty() && lowerPlain.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private static List<String> matchedKeywords(String lowerPlain, List<String> keywords) {
        List<String> found = new ArrayList<>();
        for (String kw : keywords) {
            if (!kw.isEmpty() && lowerPlain.contains(kw.toLowerCase()) && !found.contains(kw)) found.add(kw);
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

    private static LzMutableText reformat(String plain, List<String> keywords, LzStyle keywordStyle, LzStyle neutralStyle) {
        LzMutableText result = LzText.empty();
        int i = 0;
        while (i < plain.length()) {
            int[] match = findEarliestMatch(plain, i, keywords);
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

    private static int[] findEarliestMatch(String plain, int fromIdx, List<String> keywords) {
        String lower = plain.toLowerCase();
        int bestStart = -1, bestEnd = -1;
        for (String kw : keywords) {
            if (kw.isEmpty()) continue;
            int idx = lower.indexOf(kw.toLowerCase(), fromIdx);
            if (idx >= 0 && (bestStart < 0 || idx < bestStart)) {
                bestStart = idx;
                bestEnd = idx + kw.length();
            }
        }
        return bestStart < 0 ? null : new int[]{bestStart, bestEnd};
    }
}
