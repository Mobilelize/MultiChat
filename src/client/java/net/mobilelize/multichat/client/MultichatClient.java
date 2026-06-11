package net.mobilelize.multichat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.mobilelize.fabriclib.client.LzAfk;
import net.mobilelize.fabriclib.config.LzConfig;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.MessageAddedToTabCallback;
import net.mobilelize.multichat.client.command.MultiChatCommand;
import net.mobilelize.multichat.client.config.MultiChatConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.ApiTabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.NoteEntryConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.NoteTabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.PanelConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.TabConfig;
import net.mobilelize.multichat.client.config.MultiChatConfig.RuleConfig;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.ping.PingPlayer;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;

public class MultichatClient implements ClientModInitializer {

    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("multichat");
    public static MultiChatConfig CONFIG;
    public static String pendingChatText = null;

    @Override
    public void onInitializeClient() {
        CONFIG = LzConfig.load(MultiChatConfig.class);
        LzAfk.init();
        restorePanelsFromConfig();
        restoreTabsFromConfig();
        MultiChatCommand.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveTabsToConfig());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            for (ChatTab tab : ChatRenderer.TABS)
                if (tab instanceof NoteTab nt) nt.rebuildMessages();
            // Restore note tab scroll positions (done after rebuild so widths are correct)
            for (ChatPanel panel : ChatRenderer.PANELS) {
                CONFIG.panels.stream().filter(pc -> pc.id == panel.id()).findFirst()
                    .ifPresent(pc -> pc.noteScrollPositions.forEach((tabName, scroll) ->
                        ChatRenderer.TABS.stream()
                            .filter(t -> t.type() == TabType.NOTES && t.name().equals(tabName))
                            .findFirst()
                            .ifPresent(t -> {
                                var msgs = ChatRenderer.getMessages(panel, t);
                                if (!msgs.isEmpty())
                                    panel.setScrolledLines(t, Math.min(scroll, msgs.size() - 1));
                            })));
            }
            // Override active tab to first note tab if configured
            if (CONFIG.notesAlwaysOpen) {
                for (ChatPanel panel : ChatRenderer.PANELS) {
                    List<ChatTab> visible = panel.getVisibleTabs();
                    for (int idx = 0; idx < visible.size(); idx++) {
                        if (visible.get(idx).type() == TabType.NOTES) {
                            panel.setActiveTab(idx);
                            break;
                        }
                    }
                }
            }
        });

        MessageAddedToTabCallback.EVENT.register((tab, message) -> PingPlayer.playForTab(tab));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || CONFIG.modDisabled || !CONFIG.earlyMessageCapture) return;
            MessageRouter.offerRaw(LzText.wrapMc(message));
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            if (CONFIG.modDisabled || !CONFIG.earlyMessageCapture) return;
            MessageRouter.offerRaw(LzText.wrapMc(message));
        });
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    public static void saveTabsToConfig() {
        List<PanelConfig> panelOut = new ArrayList<>();
        for (ChatPanel panel : ChatRenderer.PANELS) {
            PanelConfig pc          = new PanelConfig();
            pc.id                   = panel.id();
            pc.name                 = panel.name();
            pc.enabled              = panel.enabled();
            pc.anchor               = panel.anchor();
            pc.margin               = panel.margin();
            pc.activeTab            = panel.activeTabIndex();
            ChatTab at              = panel.getActiveTab();
            pc.activeTabName        = at != null ? at.name() : "";
            pc.showOnlyWhenFocused  = panel.showOnlyWhenFocused();
            pc.showBadges           = panel.showBadges();
            pc.showTabs             = panel.showTabs();
            pc.tabsAtTop            = panel.tabsAtTop();
            pc.customX              = panel.customX();
            pc.customY              = panel.customY();
            pc.customWidth                = panel.customWidth();
            pc.customHeightLines          = panel.customHeightLines();
            pc.customUnfocusedHeightLines = panel.customUnfocusedHeightLines();
            pc.lockUnfocusedToFocused     = panel.lockUnfocusedToFocused();
            pc.customScale                = panel.customScale();
            pc.bgColor              = panel.bgColor();
            pc.customBgOpacity      = panel.customBgOpacity();
            pc.customChatOpacity    = panel.customChatOpacity();
            pc.tabBgOpacity         = panel.tabBgOpacity();
            pc.tabFgOpacity         = panel.tabFgOpacity();
            pc.panelContextTabName  = panel.panelContextTab() != null ? panel.panelContextTab().name() : "";
            pc.useActiveTabContext  = panel.useActiveTabContext();
            java.util.Map<String, Integer> scrollMap = new java.util.HashMap<>();
            for (ChatTab tab : ChatRenderer.TABS) {
                if (tab.type() == TabType.NOTES) {
                    int scroll = panel.getScrolledLines(tab);
                    if (scroll > 0) scrollMap.put(tab.name(), scroll);
                }
            }
            pc.noteScrollPositions = scrollMap;
            panelOut.add(pc);
        }
        CONFIG.panels = panelOut;

        List<TabConfig> out = new ArrayList<>();
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type() != TabType.FILTERED) continue;
            TabConfig tc               = new TabConfig();
            tc.name                    = tab.name();
            tc.differenceMode          = tab.differenceMode();
            tc.differenceLayerIndex    = tab.differenceLayerIndex();
            tc.differenceException     = tab.differenceException();
            tc.showBadge               = tab.showBadge();
            tc.showTimestamps          = tab.showTimestamps();
            tc.tabColor                = tab.tabColor();
            tc.msgBgColor              = tab.msgBgColor();
            tc.visibleOnPanels         = new java.util.ArrayList<>(tab.visibleOnPanels());
            tc.collapseRepeating       = tab.collapseRepeating();
            tc.collapseMatchFormatting = tab.collapseMatchFormatting();
            tc.messageCap              = tab.messageCap();
            tc.whitelistMode           = tab.filterRules().whitelistMode();
            tc.blacklistMode           = tab.filterRules().blacklistMode();
            tc.contextPrefix           = tab.contextPrefix();
            tc.contextCommandMode      = tab.contextCommandMode();
            tc.whitelist               = serializeRules(tab.filterRules().whitelist());
            tc.blacklist               = serializeRules(tab.filterRules().blacklist());
            tc.pingEnabled              = tab.pingEnabled();
            tc.pingSound                = tab.pingSound();
            tc.pingVolume               = tab.pingVolume();
            tc.pingPitch                = tab.pingPitch();
            tc.pingUseGlobalConditions  = tab.pingUseGlobalConditions();
            tc.pingCondAlways           = tab.pingCondAlways();
            tc.pingCondActiveTab        = tab.pingCondActiveTab();
            tc.pingCondUnfocusedTab     = tab.pingCondUnfocusedTab();
            tc.pingCondGameUnfocused    = tab.pingCondGameUnfocused();
            tc.pingCondAfk              = tab.pingCondAfk();
            tc.pingCondRequireSilence   = tab.pingCondRequireSilence();
            tc.pingCooldownSec              = tab.pingCooldownSec();
            tc.pingSilenceThresholdSec      = tab.pingSilenceThresholdSec();
            tc.pingAfkThresholdSec          = tab.pingAfkThresholdSec();
            out.add(tc);
        }
        CONFIG.tabs = out;

        List<ApiTabConfig> apiOut = new ArrayList<>();
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type() != TabType.API || tab.apiId() == null) continue;
            ApiTabConfig atc         = new ApiTabConfig();
            atc.id                       = tab.apiId();
            atc.displayName              = tab.name();
            atc.visibleOnPanels          = new java.util.ArrayList<>(tab.visibleOnPanels());
            atc.color                    = tab.tabColor();
            atc.whitelistMode            = tab.filterRules().whitelistMode();
            atc.blacklistMode            = tab.filterRules().blacklistMode();
            atc.contextPrefix            = tab.contextPrefix();
            atc.contextCommandMode       = tab.contextCommandMode();
            atc.whitelist                = serializeRules(tab.filterRules().whitelist());
            atc.blacklist                = serializeRules(tab.filterRules().blacklist());
            atc.pingEnabled              = tab.pingEnabled();
            atc.pingSound                = tab.pingSound();
            atc.pingVolume               = tab.pingVolume();
            atc.pingPitch                = tab.pingPitch();
            atc.pingUseGlobalConditions  = tab.pingUseGlobalConditions();
            atc.pingCondAlways           = tab.pingCondAlways();
            atc.pingCondActiveTab        = tab.pingCondActiveTab();
            atc.pingCondUnfocusedTab     = tab.pingCondUnfocusedTab();
            atc.pingCondGameUnfocused    = tab.pingCondGameUnfocused();
            atc.pingCondAfk              = tab.pingCondAfk();
            atc.pingCondRequireSilence   = tab.pingCondRequireSilence();
            atc.pingCooldownSec          = tab.pingCooldownSec();
            atc.pingSilenceThresholdSec  = tab.pingSilenceThresholdSec();
            atc.pingAfkThresholdSec      = tab.pingAfkThresholdSec();
            apiOut.add(atc);
        }
        CONFIG.apiTabs = apiOut;

        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type() == TabType.ALL) {
                CONFIG.allTabPanels      = new ArrayList<>(tab.visibleOnPanels());
                CONFIG.allTabRawList     = tab.allRawList();
                CONFIG.allTabRefilterRaw = tab.allRefilterRaw();
                CONFIG.allTabSearchRaw   = tab.allSearchRaw();
            } else if (tab.type() == TabType.VANILLA) {
                CONFIG.vanillaTabPanels = new ArrayList<>(tab.visibleOnPanels());
            }
        }

        List<NoteTabConfig> noteOut = new ArrayList<>();
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type() != TabType.NOTES || !(tab instanceof NoteTab nt)) continue;
            NoteTabConfig ntc     = new NoteTabConfig();
            ntc.name              = nt.name();
            ntc.visibleOnPanels   = new java.util.ArrayList<>(nt.visibleOnPanels());
            ntc.tabColor          = nt.tabColor();
            ntc.entries           = new java.util.ArrayList<>();
            for (NoteTab.NoteEntry entry : nt.noteEntries()) {
                NoteEntryConfig nec = new NoteEntryConfig();
                nec.textJson        = textToJson(entry.content());
                nec.tick            = entry.tick();
                ntc.entries.add(nec);
            }
            noteOut.add(ntc);
        }
        CONFIG.noteTabs         = noteOut;
        CONFIG.activeNoteTabName = ChatRenderer.activeNoteTab != null ? ChatRenderer.activeNoteTab.name() : "";

        CONFIG.contextTabName = ChatRenderer.contextTab != null ? ChatRenderer.contextTab.name() : "";
        CONFIG.save();
    }

    private static net.minecraft.registry.RegistryWrapper.WrapperLookup LOOKUP = null;
    private static net.minecraft.registry.RegistryWrapper.WrapperLookup lookup() {
        if (LOOKUP == null) LOOKUP = net.minecraft.registry.BuiltinRegistries.createWrapperLookup();
        return LOOKUP;
    }

    static String textToJson(LzText text) {
        try {
            return net.minecraft.text.Text.Serialization.toJsonString(text.mc(), lookup());
        } catch (Exception e) {
            return "\"" + text.mc().getString().replace("\"", "\\\"") + "\"";
        }
    }

    static LzText textFromJson(String json) {
        try {
            net.minecraft.text.Text t = net.minecraft.text.Text.Serialization.fromJson(json, lookup());
            return t != null ? LzText.wrapMc(t) : LzText.literal(json);
        } catch (Exception e) {
            return LzText.literal(json);
        }
    }

    // ------------------------------------------------------------------
    // Restore
    // ------------------------------------------------------------------

    private static void restorePanelsFromConfig() {
        if (CONFIG.panels.isEmpty()) return;

        applyPanelConfig(ChatRenderer.LEFT, CONFIG.panels.getFirst());
        ChatRenderer.LEFT.setPrimary(true);
        ChatRenderer.LEFT.setEnabled(true);
        ChatRenderer.LEFT.setId(0);
        ChatRenderer.LEFT.setLockUnfocusedToFocused(false);

        if (CONFIG.panels.size() >= 2) {
            applyPanelConfig(ChatRenderer.RIGHT, CONFIG.panels.get(1));
            ChatRenderer.RIGHT.setId(1);
        }

        for (int i = 2; i < CONFIG.panels.size(); i++) {
            ChatPanel panel = new ChatPanel();
            applyPanelConfig(panel, CONFIG.panels.get(i));
            ChatRenderer.PANELS.add(panel);
        }
    }

    private static void applyPanelConfig(ChatPanel panel, PanelConfig pc) {
        panel.setId(pc.id);
        panel.setName(pc.name.isEmpty() ? panel.name() : pc.name);
        panel.setEnabled(pc.enabled);
        panel.setAnchor(pc.anchor != null ? pc.anchor : ChatPanel.Anchor.LEFT);
        panel.setMargin(pc.margin);
        panel.setActiveTab(pc.activeTab);
        panel.setShowOnlyWhenFocused(pc.showOnlyWhenFocused);
        panel.setShowBadges(pc.showBadges);
        panel.setShowTabs(pc.showTabs);
        panel.setTabsAtTop(pc.tabsAtTop);
        panel.setCustomX(pc.customX);
        panel.setCustomY(pc.customY);
        panel.setCustomWidth(pc.customWidth);
        panel.setCustomHeightLines(pc.customHeightLines);
        panel.setCustomUnfocusedHeightLines(pc.customUnfocusedHeightLines);
        panel.setLockUnfocusedToFocused(pc.lockUnfocusedToFocused);
        panel.setCustomScale(pc.customScale);
        panel.setBgColor(pc.bgColor);
        panel.setCustomBgOpacity(pc.customBgOpacity);
        panel.setCustomChatOpacity(pc.customChatOpacity);
        panel.setTabBgOpacity(pc.tabBgOpacity);
        panel.setTabFgOpacity(pc.tabFgOpacity);
        panel.setUseActiveTabContext(pc.useActiveTabContext);
    }

    private static void restoreTabsFromConfig() {
        for (ChatTab tab : ChatRenderer.TABS) {
            if (tab.type() == TabType.ALL) {
                tab.visibleOnPanels().clear();
                tab.visibleOnPanels().addAll(CONFIG.allTabPanels);
                tab.setAllRawList(CONFIG.allTabRawList);
                tab.setAllRefilterRaw(CONFIG.allTabRefilterRaw);
                tab.setAllSearchRaw(CONFIG.allTabSearchRaw);
            } else if (tab.type() == TabType.VANILLA) {
                tab.visibleOnPanels().clear();
                tab.visibleOnPanels().addAll(CONFIG.vanillaTabPanels);
            }
        }

        ChatRenderer.TABS.removeIf(t -> t.type() == TabType.FILTERED);
        for (TabConfig tc : CONFIG.tabs) {
            ChatTab tab = new ChatTab(tc.name, TabType.FILTERED, new java.util.LinkedHashSet<>(tc.visibleOnPanels));
            tab.setDifferenceMode(tc.differenceMode);
            tab.setDifferenceLayerIndex(tc.differenceLayerIndex);
            tab.setDifferenceException(tc.differenceException);
            tab.setShowBadge(tc.showBadge);
            tab.setShowTimestamps(tc.showTimestamps);
            tab.setTabColor(tc.tabColor);
            tab.setMsgBgColor(tc.msgBgColor);
            tab.setCollapseRepeating(tc.collapseRepeating);
            tab.setCollapseMatchFormatting(tc.collapseMatchFormatting);
            tab.setMessageCap(tc.messageCap);
            tab.filterRules().setWhitelistMode(tc.whitelistMode != null ? tc.whitelistMode
                : net.mobilelize.multichat.client.filter.WhitelistMode.ANY);
            tab.filterRules().setBlacklistMode(tc.blacklistMode != null ? tc.blacklistMode
                : net.mobilelize.multichat.client.filter.WhitelistMode.ANY);
            tab.setContextPrefix(tc.contextPrefix != null ? tc.contextPrefix : "");
            tab.setContextCommandMode(tc.contextCommandMode);
            tab.setPingEnabled(tc.pingEnabled);
            tab.setPingSound(tc.pingSound != null ? tc.pingSound : "");
            tab.setPingVolume(tc.pingVolume);
            tab.setPingPitch(tc.pingPitch);
            tab.setPingUseGlobalConditions(tc.pingUseGlobalConditions);
            tab.setPingCondAlways(tc.pingCondAlways);
            tab.setPingCondActiveTab(tc.pingCondActiveTab);
            tab.setPingCondUnfocusedTab(tc.pingCondUnfocusedTab);
            tab.setPingCondGameUnfocused(tc.pingCondGameUnfocused);
            tab.setPingCondAfk(tc.pingCondAfk);
            tab.setPingCondRequireSilence(tc.pingCondRequireSilence);
            tab.setPingCooldownSec(tc.pingCooldownSec);
            tab.setPingSilenceThresholdSec(tc.pingSilenceThresholdSec);
            tab.setPingAfkThresholdSec(tc.pingAfkThresholdSec);
            for (RuleConfig rc : tc.whitelist) {
                if (rc.type != null) tab.filterRules().whitelist().add(new FilterRule(rc.type, rc.value,
                    rc.indicator != null ? rc.indicator : net.mobilelize.multichat.client.filter.IndicatorFilter.ANY));
            }
            for (RuleConfig rc : tc.blacklist) {
                if (rc.type != null) tab.filterRules().blacklist().add(new FilterRule(rc.type, rc.value,
                    rc.indicator != null ? rc.indicator : net.mobilelize.multichat.client.filter.IndicatorFilter.ANY));
            }
            ChatRenderer.TABS.add(tab);
        }

        ChatRenderer.TABS.removeIf(t -> t.type() == TabType.NOTES);
        for (NoteTabConfig ntc : CONFIG.noteTabs) {
            NoteTab nt = new NoteTab(ntc.name, new java.util.LinkedHashSet<>(ntc.visibleOnPanels));
            nt.setTabColor(ntc.tabColor);
            for (NoteEntryConfig nec : ntc.entries) {
                nt.noteEntries().add(new NoteTab.NoteEntry(textFromJson(nec.textJson), nec.tick));
            }
            ChatRenderer.TABS.add(nt);
        }

        if (ChatRenderer.TABS.stream().noneMatch(t -> t.type() == TabType.NOTES)) {
            NoteTab defaultNote = new NoteTab("Notes", java.util.List.of(0, 1));
            ChatRenderer.TABS.add(defaultNote);
        }

        if (!CONFIG.activeNoteTabName.isEmpty()) {
            ChatRenderer.activeNoteTab = (NoteTab) ChatRenderer.TABS.stream()
                .filter(t -> t.type() == TabType.NOTES && t.name().equals(CONFIG.activeNoteTabName))
                .findFirst().orElse(null);
        }
        if (ChatRenderer.activeNoteTab == null) {
            ChatRenderer.activeNoteTab = (NoteTab) ChatRenderer.TABS.stream()
                .filter(t -> t.type() == TabType.NOTES)
                .findFirst().orElse(null);
        }

        if (!CONFIG.contextTabName.isEmpty()) {
            ChatRenderer.contextTab = ChatRenderer.TABS.stream()
                .filter(t -> t.name().equals(CONFIG.contextTabName))
                .findFirst().orElse(null);
        }

        for (ChatPanel panel : ChatRenderer.PANELS) {
            String savedName = CONFIG.panels.stream()
                .filter(pc -> pc.id == panel.id())
                .map(pc -> pc.activeTabName)
                .findFirst().orElse("");
            if (!savedName.isEmpty()) {
                List<ChatTab> visible = panel.getVisibleTabs();
                boolean found = false;
                for (int idx = 0; idx < visible.size(); idx++) {
                    if (visible.get(idx).name().equals(savedName)) {
                        panel.setActiveTab(idx);
                        found = true;
                        break;
                    }
                }
                if (!found) panel.setActiveTab(panel.activeTabIndex());
            } else {
                panel.setActiveTab(panel.activeTabIndex());
            }
        }

        for (ChatPanel panel : ChatRenderer.PANELS) {
            CONFIG.panels.stream().filter(pc -> pc.id == panel.id()).findFirst().ifPresent(pc -> {
                if (!pc.panelContextTabName.isEmpty()) {
                    panel.setPanelContextTab(ChatRenderer.TABS.stream()
                        .filter(t -> t.name().equals(pc.panelContextTabName))
                        .findFirst().orElse(null));
                }
            });
        }
    }

    private static List<RuleConfig> serializeRules(List<FilterRule> rules) {
        List<RuleConfig> out = new ArrayList<>();
        for (FilterRule r : rules) {
            RuleConfig rc = new RuleConfig();
            rc.type      = r.type();
            rc.value     = r.value();
            rc.indicator = r.indicator();
            out.add(rc);
        }
        return out;
    }
}
