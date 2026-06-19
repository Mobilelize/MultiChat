package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.*;

import java.util.*;

public class MessageRouter {

    // Raw text captured at HEAD of ChatHud.addMessage before any mod modifies it.
    // Set by ChatHudMixin.captureRaw (priority 2000 = first), consumed by routeOnReturn.
    public static LzText pendingRaw = null;

    // Set before routing an API-injected message to prevent double-adding to the source tab.
    public static ChatTab skipTabOnRoute = null;

    public static void routeMessages(ChatHudLine message) {
        final LzText content  = LzText.wrapMc(message.content());
        final int    tick     = message.creationTick();
        final LzMessageIndicator indicator = LzMessageIndicator.wrapNullable(message.indicator());

        LzText rawLzText = pendingRaw;
        pendingRaw = null;
        if (rawLzText == null) rawLzText = content;


        final String fullText = rawLzText.mc().getString();

        final TreeMap<Integer, List<ChatTab>> diffByLayer = new TreeMap<>(Comparator.reverseOrder());
        for (ChatTab tab : ChatManager.getAllActivePanelsTabs()) {
            if (tab.type() == TabType.FILTERED && tab.differenceMode() && !tab.differenceException())
                diffByLayer.computeIfAbsent(tab.differenceLayerIndex(), k -> new ArrayList<>()).add(tab);
        }

        final LzChatEntry sharedEntry = new LzChatEntry(tick, rawLzText, indicator);

        for (ChatTab tab : ChatManager.getTabs()) {
            switch (tab.type()) {
                case NOTES, INFO -> { /* never receive routed messages */ }
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
            for (ChatTab tab : ChatManager.getTabs()) {
                if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                        && tab.differenceInclusion() && ChatManager.isTabInAnyEnabledPanel(tab)
                        && tab.filterRules().matches(fullText, indicator)) {
                    activeNonDiffMatched = true; break;
                }
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
