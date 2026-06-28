package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageReceivedCallback;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.SearchTab;
import net.mobilelize.multichat.client.TabType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

public class MessageRouter {

    // Raw text captured at HEAD of ChatHud.addMessage before any mod modifies it.
    // Set by ChatHudMixin.captureRaw (priority 2000 = first), consumed by routeOnReturn.
    public static LzText pendingRaw = null;

    // Set before routing an API-injected message to prevent double-adding to the source tab.
    public static ChatTab skipTabOnRoute = null;

    public static void routeMessages(ChatHudLine message) {
        final LzText content  = LzText.wrapMc(message.content());
        if (MessageReceivedCallback.EVENT.invoker().onMessageReceived(content)) return;
        final int    tick     = message.creationTick();
        final LzMessageIndicator indicator = LzMessageIndicator.wrapNullable(message.indicator());

        LzText rawLzText = pendingRaw;
        pendingRaw = null;
        if (rawLzText == null) rawLzText = content;


        final String fullText   = rawLzText.mc().getString();
        final String filterText = MultichatClient.CONFIG.normalizeFilterText
            ? FilterRule.normalizeFilterText(fullText) : fullText;

        final TreeMap<Integer, List<ChatTab>> diffByLayer = new TreeMap<>(Comparator.reverseOrder());
        for (ChatTab tab : ChatManager.getTabs()) {
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
                    if (tab.filterRules().matches(filterText, indicator))
                        ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                }
                case API -> {
                    if (tab == skipTabOnRoute) break;
                    if (!tab.filterRules().isEmpty() && tab.filterRules().matches(filterText, indicator))
                        ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                }
                case SEARCH -> {
                    final SearchTab st = (SearchTab) tab;
                    if (!st.matches(fullText, indicator, tick)) break;
                    if (st.collapseIdentical()) {
                        ChatManager.addSearchMessageWithCollapse(st, tick, content, indicator, rawLzText, sharedEntry);
                    } else {
                        ChatManager.processMessage(st, tick, content, indicator, rawLzText, sharedEntry);
                    }
                }
            }
        }

        if (diffByLayer.isEmpty()) return;

        boolean activeNonDiffMatched = false;
        for (ChatTab tab : ChatManager.getAllActivePanelsTabs()) {
            if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                    && tab.filterRules().matches(filterText, indicator)) {
                activeNonDiffMatched = true; break;
            }
        }
        if (!activeNonDiffMatched) {
            for (ChatTab tab : ChatManager.getTabs()) {
                if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                        && tab.differenceInclusion() && ChatManager.isTabInAnyEnabledPanel(tab)
                        && tab.filterRules().matches(filterText, indicator)) {
                    activeNonDiffMatched = true; break;
                }
            }
        }

        if (!activeNonDiffMatched) {
            final Set<ChatTab> activeTabs = new HashSet<>(ChatManager.getAllActivePanelsTabs());
            final boolean anyActiveDiff = diffByLayer.values().stream()
                .flatMap(List::stream).anyMatch(activeTabs::contains);

            if (!anyActiveDiff) {
                // No active diff tab: all matching diff tabs (hidden or not) get the message
                for (List<ChatTab> layerTabs : diffByLayer.values()) {
                    for (ChatTab tab : layerTabs) {
                        if (tab.filterRules().matches(filterText, indicator))
                            ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                    }
                }
            } else {
                // Active diff tab exists: process layers top-down
                // Layers with no active tab: all matching get message, continue
                // First layer with an active tab: only active matching tabs get message, stop
                for (List<ChatTab> layerTabs : diffByLayer.values()) {
                    boolean hasActiveAtLayer = layerTabs.stream().anyMatch(activeTabs::contains);
                    if (hasActiveAtLayer) {
                        for (ChatTab tab : layerTabs) {
                            if (activeTabs.contains(tab) && tab.filterRules().matches(filterText, indicator))
                                ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                        }
                        break;
                    } else {
                        for (ChatTab tab : layerTabs) {
                            if (tab.filterRules().matches(filterText, indicator))
                                ChatManager.processMessage(tab, tick, content, indicator, rawLzText, sharedEntry);
                        }
                    }
                }
            }
        }
    }
}
