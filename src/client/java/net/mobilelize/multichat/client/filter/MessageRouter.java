package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.ChatHudLine;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzMessageIndicator;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageReceivedCallback;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.HighlightEngine;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.SearchTab;
import net.mobilelize.multichat.client.TabType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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


        final String fullText   = LzText.stringSkippingObjects(rawLzText.mc());
        final String filterText = MultichatClient.CONFIG.normalizeFilterText
            ? FilterRule.normalizeFilterText(fullText) : fullText;

        final TreeMap<Integer, List<ChatTab>> diffByLayer = new TreeMap<>(Comparator.reverseOrder());
        for (ChatTab tab : ChatManager.getTabs()) {
            if (tab.type() == TabType.FILTERED && tab.differenceMode() && !tab.differenceException())
                diffByLayer.computeIfAbsent(tab.differenceLayerIndex(), k -> new ArrayList<>()).add(tab);
        }

        final LzChatEntry sharedEntry = new LzChatEntry(tick, rawLzText, indicator);

        // Highlight Words applies after filter/routing decisions (which stay based on the untouched
        // originals above) so recoloring a message can never change which tab it lands in. ALL tab
        // renders from `content`, every other tab type renders from `rawLzText` (see ChatManager.processMessage)
        // so both need their own highlighted copy for the highlight to actually show up everywhere.
        final HighlightEngine.HighlightResult highlighted = HighlightEngine.apply(content, rawLzText, sharedEntry);
        final LzText highlightedContent = highlighted.content;
        final LzText highlightedRaw     = highlighted.raw;

        // Tabs that actually end up storing this message — recorded so a later Highlight Words
        // revert click only has to touch these tabs instead of scanning every tab in the mod.
        final List<ChatTab> touchedTabs = new ArrayList<>();

        for (ChatTab tab : ChatManager.getTabs()) {
            switch (tab.type()) {
                case NOTES, INFO -> { /* never receive routed messages */ }
                case VANILLA -> { /* vanilla handles via its own addVisibleMessage */ }
                case ALL     -> {
                    ChatManager.processMessage(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry);
                    touchedTabs.add(tab);
                    routeToSubTabs(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry, filterText, rawLzText, touchedTabs);
                }
                case FILTERED -> {
                    if (tab.differenceMode() && !tab.differenceException()) break;
                    if (tab.filterRules().matches(filterText, indicator) && tab.styleRules().matches(rawLzText)) {
                        ChatManager.processMessage(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry);
                        touchedTabs.add(tab);
                        routeToSubTabs(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry, filterText, rawLzText, touchedTabs);
                    }
                }
                case API -> {
                    if (tab == skipTabOnRoute) break;
                    if (!tab.filterRules().isEmpty() && tab.filterRules().matches(filterText, indicator) && tab.styleRules().matches(rawLzText)) {
                        ChatManager.processMessage(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry);
                        touchedTabs.add(tab);
                        routeToSubTabs(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry, filterText, rawLzText, touchedTabs);
                    }
                }
                case SEARCH -> {
                    final SearchTab st = (SearchTab) tab;
                    if (!st.matches(fullText, indicator, tick)) break;
                    if (st.collapseIdentical()) {
                        ChatManager.addSearchMessageWithCollapse(st, tick, highlightedContent, indicator, highlightedRaw, sharedEntry);
                    } else {
                        ChatManager.processMessage(st, tick, highlightedContent, indicator, highlightedRaw, sharedEntry);
                    }
                    touchedTabs.add(tab);
                }
            }
        }

        if (!diffByLayer.isEmpty()) {
            boolean activeNonDiffMatched = false;
            for (ChatTab tab : ChatManager.getAllActivePanelsTabs()) {
                if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                        && tab.filterRules().matches(filterText, indicator) && tab.styleRules().matches(rawLzText)) {
                    activeNonDiffMatched = true; break;
                }
            }
            if (!activeNonDiffMatched) {
                for (ChatTab tab : ChatManager.getTabs()) {
                    if (tab.type() == TabType.FILTERED && !tab.differenceMode() && !tab.differenceException()
                            && tab.differenceInclusion() && ChatManager.isTabInAnyEnabledPanel(tab)
                            && tab.filterRules().matches(filterText, indicator) && tab.styleRules().matches(rawLzText)) {
                        activeNonDiffMatched = true; break;
                    }
                }
            }

            if (!activeNonDiffMatched) {
                for (List<ChatTab> layerTabs : diffByLayer.values()) {
                    boolean anyMatched = false;
                    for (ChatTab tab : layerTabs) {
                        if (tab.filterRules().matches(filterText, indicator) && tab.styleRules().matches(rawLzText)) {
                            ChatManager.processMessage(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry);
                            touchedTabs.add(tab);
                            routeToSubTabs(tab, tick, highlightedContent, indicator, highlightedRaw, sharedEntry, filterText, rawLzText, touchedTabs);
                            anyMatched = true;
                        }
                    }
                    if (anyMatched) break;
                }
            }
        }

        if (highlighted.actionId != null) HighlightEngine.registerRevert(highlighted.actionId, sharedEntry, content, touchedTabs);
    }

    // Layered fan-out for multi-tab sub-tabs: only called once the parent's own gate already passed
    // (parent's processMessage already ran above), so a sub-tab can only ever see what its parent let
    // through — never called independently. The auto-created "All" sub-tab has empty filterRules/styleRules,
    // which both already treat "empty" as "match everything", so it needs no special-case here.
    private static void routeToSubTabs(ChatTab parent, int tick, LzText content, LzMessageIndicator indicator,
                                        LzText raw, LzChatEntry entry, String filterText, LzText rawLzText, List<ChatTab> touchedTabs) {
        if (!parent.isMultiTab()) return;
        for (ChatTab sub : parent.subTabs()) {
            if (sub.filterRules().matches(filterText, indicator) && sub.styleRules().matches(rawLzText)) {
                ChatManager.processMessage(sub, tick, content, indicator, raw, entry);
                touchedTabs.add(sub);
            }
        }
    }
}
