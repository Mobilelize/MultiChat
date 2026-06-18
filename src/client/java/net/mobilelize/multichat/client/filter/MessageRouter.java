package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;

import java.util.*;

public class MessageRouter {

    // Queue of raw (pre-mod-modification) LzText captured via Fabric server network message events.
    // Populated by MultichatClient when earlyMessageCapture is enabled.
    private static final ArrayDeque<LzText> rawQueue = new ArrayDeque<>();

    // Set before routing an API-injected message to prevent double-adding to the source tab.
    public static ChatTab skipTabOnRoute = null;

    // Set during ChatHud.refresh() so MC's re-add of stored messages doesn't double-populate tabs.
    public static boolean suppressRouting = false;

    public static void onMessageEvent(LzText raw) {
        rawQueue.addLast(raw);
    }

    public static void routeMessages(ChatHudLine message) {
        if (suppressRouting) return;
        final LzText content  = LzText.wrapMc(message.content());
        final int    tick     = message.creationTick();
        final LzMessageIndicator indicator = LzMessageIndicator.wrapNullable(message.indicator());

        LzText rawLzText = rawQueue.pollFirst();
        if (rawLzText == null) return;

        final String fullText = rawLzText.mc().getString();

        final TreeMap<Integer, List<ChatTab>> diffByLayer = new TreeMap<>(Comparator.reverseOrder());
        for (ChatTab tab : ChatManager.getAllActivePanelsTabs()) {
            if (tab.type() == TabType.FILTERED && tab.differenceMode() && !tab.differenceException())
                diffByLayer.computeIfAbsent(tab.differenceLayerIndex(), k -> new ArrayList<>()).add(tab);
        }

        final LzChatEntry sharedEntry = new LzChatEntry(tick, rawLzText, indicator);

        for (ChatTab tab : ChatManager.getTabs()) {
            switch (tab.type()) {
                case NOTES   -> { /* notes only receive user-added entries */ }
                case VANILLA -> { /* vanilla handles via its own addVisibleMessage */ }
                case ALL     -> ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                case FILTERED -> {
                    if (tab.differenceMode() && !tab.differenceException()) break;
                    if (tab.filterRules().matches(fullText, indicator))
                        ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                }
                case API -> {
                    if (tab == skipTabOnRoute) break;
                    if (!tab.filterRules().isEmpty() && tab.filterRules().matches(fullText, indicator))
                        ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                }
                case SEARCH -> {
                    final SearchTab st = (SearchTab) tab;
                    if (!st.matches(fullText, indicator, tick)) break;
                    if (st.collapseIdentical()) {
                        final Map<ChatPanel, Integer> savedScrolls = new HashMap<>();
                        for (ChatPanel p : ChatManager.getAllActivePanels()) savedScrolls.put(p, p.getScrolledLines(st));
                        ChatManager.refilterSearch(st);
                        int maxScroll = Math.max(0, st.messageCount() - 1);
                        for (ChatPanel p : ChatManager.getAllActivePanels())
                            p.setScrolledLines(st, Math.min(savedScrolls.getOrDefault(p, 0), maxScroll));
                    } else {
                        ChatManager.processMessage(st, tick, content, indicator, rawLzText, sharedEntry);
                    }
                }
            }
        }

        boolean activeNonDiffMatched = false;
        for (ChatTab tab : ChatManager.getAllActivePanelsTabs()) {
            if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                    && tab.filterRules().matches(fullText, indicator)) {
                activeNonDiffMatched = true; break;
            }
        }

        if (!activeNonDiffMatched) {
            for (List<ChatTab> layerTabs : diffByLayer.values()) {
                boolean anyMatched = false;
                for (ChatTab tab : layerTabs) {
                    if (tab.filterRules().matches(fullText, indicator)) {
                        ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                        anyMatched = true;
                    }
                }
                if (anyMatched) break;
            }
        }
    }
}
