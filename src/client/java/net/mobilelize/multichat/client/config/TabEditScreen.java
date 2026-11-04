package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzSound;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.filter.*;
import net.mobilelize.multichat.client.ping.PingPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TabEditScreen {

    private static class MutableRule {
        FilterType      type;
        String          value;
        IndicatorFilter indicator;

        MutableRule(FilterType type, String value, IndicatorFilter indicator) {
            this.type      = type;
            this.value     = value;
            this.indicator = indicator;
        }

        FilterRule toFilterRule() { return new FilterRule(type, value, indicator); }
    }

    private static class MutableStyleCondition {
        StyleTarget        target;
        String             word;
        WordLocation       wordLocation;
        int                wordIndex;
        List<StyleFlag>    flags;
        List<StyleColor>   colors;
        List<Integer>      hexColors;

        MutableStyleCondition(StyleTarget target, String word, WordLocation wordLocation, int wordIndex,
                               List<StyleFlag> flags, List<StyleColor> colors, List<Integer> hexColors) {
            this.target       = target;
            this.word         = word;
            this.wordLocation = wordLocation;
            this.wordIndex    = wordIndex;
            this.flags        = new ArrayList<>(flags);
            this.colors       = new ArrayList<>(colors);
            this.hexColors    = new ArrayList<>(hexColors);
        }

        StyleCondition toStyleCondition() {
            return new StyleCondition(target, word, wordLocation, wordIndex, flags, colors, hexColors);
        }
    }

    public static Screen create(Screen parent, ChatTab tab) {
        // Cloth Config's Save runs every entry's own setSaveConsumer in per-CATEGORY order (verified
        // against cloth-config-fabric's actual saveAll: it iterates categorizedEntries, a
        // LinkedHashMap keyed by category-creation order, calling every entry in one category before
        // the next starts) — never whole-screen add-order. "Settings" is the first category created
        // on this screen, before Whitelist/Blacklist/Style Whitelist/Style Blacklist, so a
        // setSaveConsumer on the "Refilter on Save" toggle (which lives in Settings) would always run
        // before those categories' consumers write their rule edits, no matter where within Settings
        // it's placed. setSavingRunnable is the one hook Cloth Config calls strictly after every
        // category's every entry has saved (AbstractConfigScreen.saveAll -> save() -> savingRunnable),
        // so the refilter is deferred into this closure instead and only fires if the toggle was on.
        boolean[] refilterOnSave = {false};
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(LzText.literal("Edit Tab: " + tab.name()).mc())
            .setSavingRunnable(() -> {
                MultichatClient.saveTabsToConfig();
                if (refilterOnSave[0]) ChatManager.refilterTab(tab);
            });

        ConfigEntryBuilder eb = builder.entryBuilder();

        if (tab.type() == TabType.ALL) {
            ConfigCategory settings = builder.getOrCreateCategory(LzText.literal("Settings").mc());
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Badge").mc(), tab.showBadge())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show unread count badge on this tab.").mc())
                .setSaveConsumer(tab::setShowBadge)
                .build());
            return builder.build();
        }

        // ── Settings ──────────────────────────────────────────────────────
        ConfigCategory settings = builder.getOrCreateCategory(LzText.literal("Settings").mc());

        if (tab.type() == TabType.FILTERED || tab.type() == TabType.API) {
            settings.addEntry(eb.startStrField(LzText.literal("Name").mc(), tab.name())
                .setDefaultValue(tab.name())
                .setErrorSupplier(v -> {
                    if (v.trim().isEmpty()) return Optional.of(LzText.literal("Name cannot be empty.").mc());
                    if (tab.apiId() == null) {
                        // Sub-tabs aren't in the global tab list — only need to be unique among siblings.
                        ChatTab owner = ChatManager.findParentOf(tab);
                        boolean clash = owner != null
                            ? owner.subTabs().stream().anyMatch(t -> t != tab && t.name().equalsIgnoreCase(v.trim()))
                            : ChatManager.getTabs().stream()
                                .filter(t -> t != tab && t.type() == TabType.FILTERED)
                                .anyMatch(t -> t.name().equalsIgnoreCase(v.trim()));
                        if (clash) return Optional.of(LzText.literal("A tab with that name already exists.").mc());
                    }
                    return Optional.empty();
                })
                .setSaveConsumer(v -> tab.setName(v.trim()))
                .build());

            settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Timestamps").mc(), tab.showTimestamps())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Prepend a timestamp to each message.").mc())
                .setSaveConsumer(tab::setShowTimestamps)
                .build());

            settings.addEntry(eb.startBooleanToggle(LzText.literal("Collapse Repeating").mc(), tab.collapseRepeating())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Stack identical messages with a counter.").mc())
                .setSaveConsumer(tab::setCollapseRepeating)
                .build());

            settings.addEntry(eb.startBooleanToggle(LzText.literal("Collapse: Match Formatting").mc(), tab.collapseMatchFormatting())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Only collapse if formatting is also identical.").mc())
                .setSaveConsumer(tab::setCollapseMatchFormatting)
                .build());

            settings.addEntry(eb.startIntField(LzText.literal("Message Cap").mc(), tab.messageCap())
                .setDefaultValue(0)
                .setMin(0)
                .setTooltip(LzText.literal("Max messages stored. 0 = unlimited.").mc())
                .setSaveConsumer(tab::setMessageCap)
                .build());

            settings.addEntry(eb.startBooleanToggle(LzText.literal("Force Highlight Matches").mc(), tab.forceHighlightMatches())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Let messages that matched a Highlight Words keyword into this tab even if they fail this tab's own filter rules.").mc())
                .setSaveConsumer(tab::setForceHighlightMatches)
                .build());
        }


        if (tab.type() == TabType.FILTERED || tab.type() == TabType.API) {
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Show Badge").mc(), tab.showBadge())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("Show unread count badge on this tab.").mc())
                .setSaveConsumer(tab::setShowBadge)
                .build());

            settings.addEntry(eb.startColorField(LzText.literal("Tab Color").mc(), tab.tabColor())
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Custom tab color. 000000 = default gray.").mc())
                .setSaveConsumer(tab::setTabColor)
                .build());

            settings.addEntry(eb.startStrField(LzText.literal("Context Prefix").mc(), tab.contextPrefix())
                .setDefaultValue("")
                .setTooltip(LzText.literal("Prefix prepended when this tab's context is active.").mc())
                .setSaveConsumer(v -> tab.setContextPrefix(v.trim()))
                .build());

            settings.addEntry(eb.startBooleanToggle(LzText.literal("Context Command Mode").mc(), tab.contextCommandMode())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("ON: sent as command. OFF: sent as chat text.").mc())
                .setSaveConsumer(tab::setContextCommandMode)
                .build());

            settings.addEntry(eb.startBooleanToggle(LzText.literal("Context Prefix Space").mc(), !tab.contextNoSpace())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("ON: a space is added between the prefix and your message. OFF: message is appended directly after the prefix.").mc())
                .setSaveConsumer(v -> tab.setContextNoSpace(!v))
                .build());
        }

        if (tab.type() == TabType.FILTERED) {
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Multi Tab").mc(), tab.isMultiTab())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Gives this tab sub-tabs. Messages that pass this tab's own filter are further split across its sub-tabs by their own rules. Click this tab in chat to drill into its sub-tabs; right-click any sub-tab → Collapse Sub-tabs to back out.").mc())
                .setSaveConsumer(tab::setMultiTab)
                .build());

            settings.addEntry(eb.startStrList(
                    LzText.literal("Sub-Tabs").mc(),
                    tab.subTabs().stream().skip(1).map(ChatTab::name).collect(Collectors.toList()))
                .setDefaultValue(List.of())
                .setTooltip(LzText.literal("Extra sub-tabs for this Multi Tab, besides the built-in \"All\". Enable Multi Tab above first. Each sub-tab's own whitelist/blacklist rules are edited by opening it here as its own tab (Edit Tab), same as any other tab.").mc())
                .setSaveConsumer(names -> {
                    if (!tab.isMultiTab()) return;
                    List<ChatTab> subs = tab.subTabs();
                    ChatTab all = subs.isEmpty() ? null : subs.get(0);
                    List<ChatTab> rest = subs.size() > 1 ? new ArrayList<>(subs.subList(1, subs.size())) : new ArrayList<>();
                    List<ChatTab> newRest = new ArrayList<>();
                    for (String name : names) {
                        String trimmed = name.trim();
                        if (trimmed.isEmpty()) continue;
                        ChatTab existing = rest.stream().filter(s -> s.name().equals(trimmed)).findFirst().orElse(null);
                        if (existing != null) { rest.remove(existing); newRest.add(existing); }
                        else newRest.add(new ChatTab(trimmed, TabType.FILTERED, Collections.emptyList()));
                    }
                    subs.clear();
                    if (all != null) subs.add(all);
                    subs.addAll(newRest);
                })
                .build());
        }

        if (tab.type() != TabType.FILTERED && tab.type() != TabType.API) return builder.build();

        // ── Sound Pings ────────────────────────────────────────────────────
        ConfigCategory pingsCat = builder.getOrCreateCategory(LzText.literal("Sound Pings").mc());

        pingsCat.addEntry(eb.startBooleanToggle(LzText.literal("Enable Sound Ping").mc(), tab.pingEnabled())
            .setDefaultValue(false)
            .setTooltip(LzText.literal("Play a sound when a message arrives in this tab. Requires global switch on in Settings → Sound Pings.").mc())
            .setSaveConsumer(tab::setPingEnabled)
            .build());

        // Holders keep effective values in sync as save consumers fire — order-independent
        final String   tabSoundAtOpen = tab.pingSound(); // "" = inheriting global
        final String[] snd = { tabSoundAtOpen.isEmpty() ? MultichatClient.CONFIG.pingSound : tabSoundAtOpen };
        final float[]  vol = { tab.pingVolume() < 0 ? MultichatClient.CONFIG.pingVolume : tab.pingVolume() };
        final float[]  pit = { tab.pingPitch()  < 0 ? MultichatClient.CONFIG.pingPitch  : tab.pingPitch() };

        pingsCat.addEntry(eb.startDropdownMenu(
                LzText.literal("Ping Sound").mc(),
                DropdownMenuBuilder.TopCellElementBuilder.of(snd[0], s -> s),
                DropdownMenuBuilder.CellCreatorBuilder.of())
                .setDefaultValue(MultichatClient.CONFIG.pingSound)
                .setSelections(PingPlayer.allSoundIds())
                .setTooltip(LzText.literal("Sound ID. Clear to inherit from global default.").mc())
                .setSaveConsumer(v -> {
                    String s = v.trim();
                    // Preserve inheritance: if tab was inheriting and user left the global sound, keep empty
                    String toSave = (tabSoundAtOpen.isEmpty() && s.equals(MultichatClient.CONFIG.pingSound)) ? "" : s;
                    tab.setPingSound(toSave);
                    snd[0] = toSave.isEmpty() ? MultichatClient.CONFIG.pingSound : toSave;
                })
                .build());

        pingsCat.addEntry(SoundBrowser.build(eb, PingPlayer.allSoundIds(), soundId -> {
            tab.setPingSound(soundId);
            snd[0] = soundId;
            LzSound.playMaster(soundId, pit[0], vol[0]);
        }));

        pingsCat.addEntry(eb.startIntSlider(
                LzText.literal("Ping Volume").mc(),
                MultiChatConfig.pingVolPct(tab.pingVolume()), -1, 200)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("0–200%. -1 = inherit global default.").mc())
                .setSaveConsumer(v -> {
                    tab.setPingVolume(MultiChatConfig.fromPingVolPct(v));
                    vol[0] = v < 0 ? MultichatClient.CONFIG.pingVolume : MultiChatConfig.fromPingVolPct(v);
                })
                .build());

        pingsCat.addEntry(eb.startIntSlider(
                LzText.literal("Ping Pitch").mc(),
                MultiChatConfig.pingPitchPct(tab.pingPitch()), -1, 200)
                .setDefaultValue(-1)
                .setTooltip(LzText.literal("50–200. -1 = inherit global default.").mc())
                .setSaveConsumer(v -> {
                    tab.setPingPitch(MultiChatConfig.fromPingPitchPct(v));
                    pit[0] = v < 0 ? MultichatClient.CONFIG.pingPitch : MultiChatConfig.fromPingPitchPct(v);
                })
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Test Ping Sound").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Toggle ON then Save to play the current sound settings.").mc())
                .setSaveConsumer(v -> { if (v) LzSound.playMaster(snd[0].isEmpty() ? MultichatClient.CONFIG.pingSound : snd[0], pit[0], vol[0]); })
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Use Global Ping Conditions").mc(),
                tab.pingUseGlobalConditions())
                .setDefaultValue(true)
                .setTooltip(LzText.literal("When ON, this tab uses the global ping conditions from Settings → Sound Pings. Turn OFF to override with tab-specific conditions below.").mc())
                .setSaveConsumer(tab::setPingUseGlobalConditions)
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping Always").mc(),
                tab.pingCondAlways())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping on every message regardless of focus or AFK state.").mc())
                .setSaveConsumer(tab::setPingCondAlways)
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when Tab Active").mc(),
                tab.pingCondActiveTab())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping even when this tab is currently visible on a panel. Useful when you need an audio alert regardless of whether you're watching the tab.").mc())
                .setSaveConsumer(tab::setPingCondActiveTab)
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when Tab Unfocused").mc(),
                tab.pingCondUnfocusedTab())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping when this tab is not active on any panel.").mc())
                .setSaveConsumer(tab::setPingCondUnfocusedTab)
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when Game Unfocused").mc(),
                tab.pingCondGameUnfocused())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping when the Minecraft window does not have OS focus.").mc())
                .setSaveConsumer(tab::setPingCondGameUnfocused)
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Ping when AFK").mc(),
                tab.pingCondAfk())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Ping when mouse has not moved for AFK Threshold seconds.").mc())
                .setSaveConsumer(tab::setPingCondAfk)
                .build());

        pingsCat.addEntry(eb.startBooleanToggle(
                LzText.literal("Require Silence").mc(),
                tab.pingCondRequireSilence())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("AND gate: the gap between the previous message and this one must be >= Silence Threshold. Any message resets the clock — pure inter-message silence.").mc())
                .setSaveConsumer(tab::setPingCondRequireSilence)
                .build());

        pingsCat.addEntry(eb.startIntField(
                LzText.literal("Silence Threshold (s)").mc(),
                tab.pingSilenceThresholdSec())
                .setDefaultValue(-1)
                .setMin(-1)
                .setTooltip(LzText.literal("Seconds of no messages before Require Silence triggers. -1 = inherit global.").mc())
                .setSaveConsumer(tab::setPingSilenceThresholdSec)
                .build());

        pingsCat.addEntry(eb.startIntField(
                LzText.literal("AFK Threshold (s)").mc(),
                tab.pingAfkThresholdSec())
                .setDefaultValue(-1)
                .setMin(-1)
                .setTooltip(LzText.literal("Seconds without mouse movement before considered AFK. -1 = inherit global.").mc())
                .setSaveConsumer(tab::setPingAfkThresholdSec)
                .build());

        pingsCat.addEntry(eb.startIntField(
                LzText.literal("Ping Cooldown (s)").mc(),
                tab.pingCooldownSec())
                .setDefaultValue(-1)
                .setMin(-1)
                .setTooltip(LzText.literal("Min seconds between pings. -1 = inherit global. 0 = no cooldown.").mc())
                .setSaveConsumer(tab::setPingCooldownSec)
                .build());

        // ── Difference Mode ────────────────────────────────────────────────
        if (tab.type() == TabType.FILTERED && ChatManager.findParentOf(tab) == null) {
            ConfigCategory diffCat = builder.getOrCreateCategory(LzText.literal("Difference Mode").mc());

            diffCat.addEntry(eb.startBooleanToggle(LzText.literal("Difference Mode").mc(), tab.differenceMode())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Routes messages through a layered pipeline. Lower-index layers claim messages first.").mc())
                .setSaveConsumer(tab::setDifferenceMode)
                .build());

            diffCat.addEntry(eb.startIntField(LzText.literal("Difference Layer").mc(), tab.differenceLayerIndex())
                .setDefaultValue(0)
                .setTooltip(LzText.literal("Layer index. Higher = higher priority. All matching tabs at the same index receive the message together. Negative numbers allowed.").mc())
                .setSaveConsumer(tab::setDifferenceLayerIndex)
                .build());

            diffCat.addEntry(eb.startBooleanToggle(LzText.literal("Difference Exception").mc(), tab.differenceException())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("This tab receives messages normally but never blocks the pipeline. Other diff tabs are unaffected by what this tab matches.").mc())
                .setSaveConsumer(tab::setDifferenceException)
                .build());

            diffCat.addEntry(eb.startBooleanToggle(LzText.literal("Difference Inclusion").mc(), tab.differenceInclusion())
                .setDefaultValue(false)
                .setTooltip(LzText.literal("When enabled, this tab blocks the difference pipeline even when it is not the active tab — as long as it is assigned to any enabled panel.").mc())
                .setSaveConsumer(tab::setDifferenceInclusion)
                .build());
        }

        // ── Whitelist ──────────────────────────────────────────────────────
        ConfigCategory wlCat = builder.getOrCreateCategory(LzText.literal("Whitelist").mc());

        wlCat.addEntry(eb.startEnumSelector(LzText.literal("Whitelist Mode").mc(), WhitelistMode.class,
                tab.filterRules().whitelistMode())
            .setDefaultValue(WhitelistMode.ANY)
            .setTooltip(LzText.literal("ANY: match any rule. ALL: must match every rule.").mc())
            .setSaveConsumer(tab.filterRules()::setWhitelistMode)
            .build());

        wlCat.addEntry(buildRuleList(eb, LzText.literal("Whitelist"), tab.filterRules().whitelist(), list -> {
            tab.filterRules().whitelist().clear();
            list.forEach(mr -> tab.filterRules().whitelist().add(mr.toFilterRule()));
        }));

        // ── Blacklist ──────────────────────────────────────────────────────
        ConfigCategory blCat = builder.getOrCreateCategory(LzText.literal("Blacklist").mc());

        blCat.addEntry(eb.startEnumSelector(LzText.literal("Blacklist Mode").mc(), BlacklistMode.class,
                tab.filterRules().blacklistMode())
            .setDefaultValue(BlacklistMode.ANY)
            .setTooltip(LzText.literal("ANY: exclude if any rule matches. ALL: exclude only if all rules match.").mc())
            .setSaveConsumer(tab.filterRules()::setBlacklistMode)
            .build());

        blCat.addEntry(buildRuleList(eb, LzText.literal("Blacklist"), tab.filterRules().blacklist(), list -> {
            tab.filterRules().blacklist().clear();
            list.forEach(mr -> tab.filterRules().blacklist().add(mr.toFilterRule()));
        }));

        // ── Style Whitelist / Blacklist ──────────────────────────────────
        // Separate from the text Whitelist/Blacklist above — these check actual rendering style
        // (bold/italic/color), not text. A message must satisfy both to be routed here. Same
        // whitelist/blacklist shape as the text rules, just style-based instead of text-based.
        ConfigCategory styleWlCat = builder.getOrCreateCategory(LzText.literal("Style Whitelist").mc());

        styleWlCat.addEntry(eb.startEnumSelector(LzText.literal("Style Whitelist Mode").mc(), WhitelistMode.class,
                tab.styleRules().whitelistMode())
            .setDefaultValue(WhitelistMode.ANY)
            .setTooltip(LzText.literal("ANY: match any condition. ALL: must match every condition.").mc())
            .setSaveConsumer(tab.styleRules()::setWhitelistMode)
            .build());

        styleWlCat.addEntry(buildStyleConditionList(eb, LzText.literal("Style Whitelist"), tab.styleRules().whitelist(), list -> {
            tab.styleRules().whitelist().clear();
            list.forEach(mc -> tab.styleRules().whitelist().add(mc.toStyleCondition()));
        }));

        // ── Style Blacklist ───────────────────────────────────────────────
        ConfigCategory styleBlCat = builder.getOrCreateCategory(LzText.literal("Style Blacklist").mc());

        styleBlCat.addEntry(eb.startEnumSelector(LzText.literal("Style Blacklist Mode").mc(), BlacklistMode.class,
                tab.styleRules().blacklistMode())
            .setDefaultValue(BlacklistMode.ANY)
            .setTooltip(LzText.literal("ANY: exclude if any condition matches. ALL: exclude only if all conditions match.").mc())
            .setSaveConsumer(tab.styleRules()::setBlacklistMode)
            .build());

        styleBlCat.addEntry(buildStyleConditionList(eb, LzText.literal("Style Blacklist"), tab.styleRules().blacklist(), list -> {
            tab.styleRules().blacklist().clear();
            list.forEach(mc -> tab.styleRules().blacklist().add(mc.toStyleCondition()));
        }));

        if (tab.type() == TabType.FILTERED) {
            // Just captures the toggle's value — the actual refilter runs from setSavingRunnable
            // above, after every category (including Whitelist/Blacklist/Style rules below) has
            // already saved its own edits. See the comment on builder's setSavingRunnable for why.
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Refilter on Save").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Rescan all messages and rebuild this tab on save.").mc())
                .setSaveConsumer(v -> refilterOnSave[0] = v)
                .build());
        }

        addHelp(builder, eb, tab);
        return builder.build();
    }

    // ── Help ──────────────────────────────────────────────────────────────
    // One collapsible section per part of this screen (plus what the tab can do from chat), so the
    // categories themselves stay just settings. Sections for features this tab can't use are left out.
    private static void addHelp(ConfigBuilder builder, ConfigEntryBuilder eb, ChatTab tab) {
        ConfigCategory help = builder.getOrCreateCategory(LzText.literal("Help").mc());
        boolean filtered = tab.type() == TabType.FILTERED;
        boolean subTab   = ChatManager.findParentOf(tab) != null;

        help.addEntry(helpSection(eb, "How messages get into this tab", """
                &7Every incoming message is checked against this tab's rules. It lands here only if it
                &7passes &fall four&7 lists: &fWhitelist&7, &fBlacklist&7, &fStyle Whitelist&7 and
                &fStyle Blacklist&7.
                  &f• &7An &fempty&7 whitelist lets everything through; an empty blacklist blocks nothing.
                  &f• &7Blacklists are checked first — a blacklist match always keeps the message out.
                  &f• &7Text rules check what the message &fsays&7, style rules check how it &flooks&7.
                &7Changes only affect new messages unless you turn on &fRefilter on Save&7 (Settings),
                &7which rescans the whole history with the new rules when you save."""));

        help.addEntry(helpSection(eb, "Settings", """
                  &fName&7 — the tab's label. Must be unique (among its siblings, for a sub-tab).
                  &fShow Timestamps&7 — puts the time in front of every message in this tab.
                  &fCollapse Repeating&7 — the same message twice in a row is stacked with a counter
                    instead of repeated. &fMatch Formatting&7 only stacks it if the colors match too.
                  &fMessage Cap&7 — most messages this tab keeps; the oldest go first. 0 = unlimited.
                  &fForce Highlight Matches&7 — a message that triggers Highlight Words gets in even if
                    it fails this tab's rules.
                  &fShow Badge&7 — the unread counter on the tab. &fTab Color&7 — custom tab color
                    (000000 = default).
                  &fRefilter on Save&7 — rescan all messages with the rules from this screen on save."""));

        help.addEntry(helpSection(eb, "Context prefix", """
                &7Lets you talk "in" this tab: while its context is on, whatever you type is sent with
                &fContext Prefix&7 in front, e.g. prefix &f/msg Steve&7 turns "hi" into "/msg Steve hi".
                  &f• &7Turn it on or off with right-click on the tab → &fToggle Context&7. The chat box
                    shows the active prefix above it.
                  &f• &fContext Command Mode&7 ON sends the result as a command, OFF as normal chat.
                  &f• &fContext Prefix Space&7 OFF glues your message straight onto the prefix.
                  &f• &7Messages starting with "/" are never prefixed."""));

        if (filtered) {
            help.addEntry(helpSection(eb, "Multi Tab (sub-tabs)", """
                    &7Turns this tab into a folder of sub-tabs. A sub-tab only ever sees messages this
                    &7tab already let in, then filters them again with its own rules.
                      &f• &7Turn on &fMulti Tab&7, then add names under &fSub-Tabs&7. A built-in &fAll&7
                        sub-tab always shows everything this tab let in.
                      &f• &7In chat, click the tab to open its sub-tabs. The red &fX&7, or right-click →
                        &fCollapse Sub-tabs&7, goes back.
                      &f• &7Edit a sub-tab's own rules with right-click on it → &fEdit Tab&7."""));
        }

        help.addEntry(helpSection(eb, "Sound Pings", """
                &7Plays a sound when a message arrives here. Needs the global switch in
                &fSettings → Sound Pings&7 on as well.
                  &f• &fPing Sound / Volume / Pitch&7 — leave at the default (-1) to use the global ones.
                    &fTest Ping Sound&7: turn on, then Save, to hear it.
                  &f• &fUse Global Ping Conditions&7 ON uses the global "when" rules. Turn it OFF to use
                    the conditions on this tab: a ping fires if &fany&7 enabled one is true
                    (Always, Tab Active, Tab Unfocused, Game Unfocused, AFK).
                  &f• &fRequire Silence&7 is extra: the gap since the previous message must be at least
                    &fSilence Threshold&7. &fPing Cooldown&7 is the minimum time between two pings."""));

        if (filtered && !subTab) {
            help.addEntry(helpSection(eb, "Difference Mode", """
                    &7Makes tabs share messages instead of each showing a copy: a message goes only to
                    &7the matching tab(s) on the &fhighest Difference Layer&7.
                      &f• &7Tabs on the same layer that match all receive it together.
                      &f• &fDifference Exception&7 — this tab still receives messages but never takes
                        them away from the others.
                      &f• &fDifference Inclusion&7 — this tab claims messages even when it isn't the
                        active tab, as long as it's on an enabled panel.
                    &eExample&r: &aGuild&r layer &e2&r, &aTrade&r layer &e2&r, &6Other&r layer &e0
                    &7A guild message matches &aGuild&7 (layer 2, highest), so &6Other&7 never sees it.
                    &7A message matching neither layer-2 tab falls down to &6Other&7 at layer 0."""));
        }

        help.addEntry(helpSection(eb, "Whitelist & Blacklist (text rules)", """
                &7Each rule checks the message's text (not case-sensitive, except regex):
                  &fCONTAINS / STARTS_WITH / ENDS_WITH / EXACT&7 — plain text checks.
                  &fWORD&7 — the value as a whole word ("ban" won't match "urban").
                  &fFUZZY&7 — all letters of the value appear in order, with anything in between.
                  &fREGEX / REGEX_START / REGEX_END&7 — a regular expression anywhere / at the start /
                    at the end of the message.
                &fIndicator&7 narrows a rule to one kind of message: &fPLAYER&7 chat, &fSYSTEM&7 messages,
                &fNOT_SECURE&7, &fMODIFIED&7 or &fCHAT_ERROR&7 (&fANY&7 = no restriction).
                &fMode&7: whitelist &fANY&7 needs one rule to match, &fALL&7 needs every rule. Blacklist
                &fANY&7 blocks when one matches, &fALL&7 only when all match."""));

        help.addEntry(helpSection(eb, "Style Whitelist & Blacklist", """
                &7Checks how part of the message &frenders&7, not what it says. Empty list = no
                &7restriction. Each condition has three small sections:
                  &fFormatting&7 — every style toggled on must be present at once (e.g. bold AND
                    italic), plus click/hover checks (has a link, runs a command, shows a tooltip...).
                  &fColors&7 — the named colors allowed; &fHex Colors&7 — raw RGB values for any other
                    color. Every character checked must be ONE of them, not necessarily the same one,
                    so a word with a different color per letter still matches.
                &7Leave a section empty to not check it. At least one must be set.
                &fTarget&7 decides where to look:
                  &fWORD&7 — find a word and check its style. &fWord Location&7: &fANYWHERE&7 in the
                    message, &fSTART&7 / &fEND&7 (first / last word), or &fINDEX&7 with &fWord Index&7
                    (1 = first word). E.g. word "Jack" + START = the first word is Jack AND styled so.
                  &fSTART_OF_MESSAGE&7 / &fEND_OF_MESSAGE&7 — the first / last word's style.
                &7Leave &fWord&7 blank to check the style at that spot whatever it says (e.g. "is the
                &7sender's name italic"). With &fANYWHERE&7, blank checks whether the style appears
                &fanywhere&7 in the message — handy for links and names.
                &7Formatting must cover the whole word; click/hover only needs one character of it.
                &fMode&7 works like the text lists. The style blacklist keeps a message out instead."""));

        help.addEntry(helpSection(eb, "Right-click a tab in chat", """
                  &fEdit Tab&7 / &fRename Tab&7 — open this screen / rename quickly.
                  &fToggle Context&7 — use the context prefix (see Context prefix).
                  &fTimestamps&7 / &fCollapse&7 — quick toggles for those settings.
                  &fClear Messages&7, &fClear Badge&7, &fToggle Badge&7.
                  &fSearch in tab…&7 — search only this tab's messages.
                  &fScroll to Top / Bottom&7, &fExport All&7 — copies the tab's messages to the clipboard.
                  &fMove Left / Right&7 — reorder tabs.
                  &fPop Out&7 — gives the tab its own floating panel (&fClose Popout&7 removes it).
                  &fCollapse Sub-tabs&7 — back out of a multi tab."""));

        help.addEntry(helpSection(eb, "Right-click a message", """
                  &fCopy&7 / &fCopy with Timestamp&7 / &fCopy Selected&7 — copy the message or your
                    drag-selection.
                  &fSearch for this&7 — open a search with the message's text.
                  &fAdd to Notes&7 — save it to your active note tab.
                  &fJump to All&7 — find the same message in the All tab.
                  &fDelete message&7 — remove it from this tab. &fDelete from All&7 — remove it from
                    every tab that has it."""));
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> helpSection(
            ConfigEntryBuilder eb, String title, String body) {
        SubCategoryBuilder section = eb.startSubCategory(LzText.literal(title).mc()).setExpanded(false);
        section.add(eb.startTextDescription(LzText.format(body).mc()).build());
        return section.build();
    }

    private static NestedListListEntry<MutableRule, MultiElementListEntry<MutableRule>> buildRuleList(
            ConfigEntryBuilder eb, LzText label, List<FilterRule> source, Consumer<List<MutableRule>> saveConsumer) {

        List<MutableRule> rules = source.stream()
            .map(r -> new MutableRule(r.type(), r.value(), r.indicator()))
            .collect(Collectors.toCollection(ArrayList::new));

        return new NestedListListEntry<>(
            label.mc(),
            rules,
            true,
            Optional::empty,
            saveConsumer,
            ArrayList::new,
            eb.getResetButtonKey(),
            true,
            true,
            (elem, nestedList) -> {
                MutableRule rule = elem != null ? elem : new MutableRule(FilterType.CONTAINS, "", IndicatorFilter.ANY);
                return new MultiElementListEntry<>(
                    LzText.literal("Rule " + ruleNumber(nestedList.getValue(), rule)).mc(),
                    rule,
                    List.of(
                        eb.startEnumSelector(LzText.literal("Type").mc(), FilterType.class, rule.type)
                            .setDefaultValue(FilterType.CONTAINS)
                            .setSaveConsumer(v -> rule.type = v)
                            .build(),
                        eb.startStrField(LzText.literal("Value").mc(), rule.value)
                            .setDefaultValue("")
                            .setSaveConsumer(v -> rule.value = v)
                            .build(),
                        eb.startEnumSelector(LzText.literal("Indicator").mc(), IndicatorFilter.class, rule.indicator)
                            .setDefaultValue(IndicatorFilter.ANY)
                            .setSaveConsumer(v -> rule.indicator = v)
                            .build()
                    ),
                    true
                );
            }
        );
    }

    private static NestedListListEntry<MutableStyleCondition, MultiElementListEntry<MutableStyleCondition>> buildStyleConditionList(
            ConfigEntryBuilder eb, LzText label, List<StyleCondition> source, Consumer<List<MutableStyleCondition>> saveConsumer) {

        List<MutableStyleCondition> conditions = source.stream()
            .map(c -> new MutableStyleCondition(c.target(), c.word(), c.wordLocation(), c.wordIndex(), c.flags(), c.colors(), c.hexColors()))
            .collect(Collectors.toCollection(ArrayList::new));

        return new NestedListListEntry<>(
            label.mc(),
            conditions,
            true,
            Optional::empty,
            saveConsumer,
            ArrayList::new,
            eb.getResetButtonKey(),
            true,
            true,
            (elem, nestedList) -> {
                MutableStyleCondition cond = elem != null
                    ? elem : new MutableStyleCondition(StyleTarget.WORD, "", WordLocation.ANYWHERE, 1, List.of(), List.of(), List.of());
                return new MultiElementListEntry<>(
                    LzText.literal("Condition " + ruleNumber(nestedList.getValue(), cond)).mc(),
                    cond,
                    List.of(
                        eb.startEnumSelector(LzText.literal("Target").mc(), StyleTarget.class, cond.target)
                            .setDefaultValue(StyleTarget.WORD)
                            .setSaveConsumer(v -> cond.target = v)
                            .build(),
                        eb.startStrField(LzText.literal("Word").mc(), cond.word)
                            .setDefaultValue("")
                            .setTooltip(LzText.literal("Only used when Target = WORD. Blank + START/END/INDEX = check that word's style without caring what it says.").mc())
                            .setSaveConsumer(v -> cond.word = v)
                            .build(),
                        eb.startEnumSelector(LzText.literal("Word Location").mc(), WordLocation.class, cond.wordLocation)
                            .setDefaultValue(WordLocation.ANYWHERE)
                            .setTooltip(LzText.literal("Only used when Target = WORD. Where the word is allowed to be found.").mc())
                            .setSaveConsumer(v -> cond.wordLocation = v)
                            .build(),
                        eb.startIntField(LzText.literal("Word Index").mc(), cond.wordIndex)
                            .setDefaultValue(1)
                            .setMin(1)
                            .setTooltip(LzText.literal("Only used when Word Location = INDEX. 1 = first word, 2 = second word, etc.").mc())
                            .setSaveConsumer(v -> cond.wordIndex = v)
                            .build(),
                        buildFlagCategory(eb, cond),
                        buildColorCategory(eb, cond),
                        buildHexColorList(eb, LzText.literal("Hex Colors"), cond.hexColors, list -> cond.hexColors = list)
                    ),
                    true
                );
            }
        );
    }

    /** 1-based position of {@code elem} in {@code current} (identity match), or the next free
     *  slot ({@code current.size() + 1}) when it's a brand-new element not yet added to the list. */
    private static <T> int ruleNumber(List<T> current, T elem) {
        for (int i = 0; i < current.size(); i++) if (current.get(i) == elem) return i + 1;
        return current.size() + 1;
    }

    /** All 5 flags pre-populated as toggles — every one turned on must be present at once (AND). */
    private static AbstractConfigListEntry<?> buildFlagCategory(ConfigEntryBuilder eb, MutableStyleCondition cond) {
        SubCategoryBuilder cat = eb.startSubCategory(LzText.literal("Formatting").mc()).setExpanded(false);
        for (StyleFlag f : StyleFlag.values()) {
            cat.add(eb.startBooleanToggle(LzText.literal(capitalize(f.name())).mc(), cond.flags.contains(f))
                .setDefaultValue(false)
                .setSaveConsumer(v -> {
                    if (v) { if (!cond.flags.contains(f)) cond.flags.add(f); }
                    else cond.flags.remove(f);
                })
                .build());
        }
        return cat.build();
    }

    /** All 16 named colors pre-populated as toggles (NONE excluded — meaningless as a toggle). Each
     *  character in range must be one of the toggled-on colors or hex colors below (not necessarily the
     *  same one throughout) — a multi-colored word can still match as long as every color it uses is
     *  somewhere in one of the two lists. */
    private static AbstractConfigListEntry<?> buildColorCategory(ConfigEntryBuilder eb, MutableStyleCondition cond) {
        SubCategoryBuilder cat = eb.startSubCategory(LzText.literal("Colors").mc()).setExpanded(false);
        for (StyleColor c : StyleColor.values()) {
            if (c == StyleColor.NONE) continue;
            cat.add(eb.startBooleanToggle(LzText.literal(capitalize(c.name())).mc(), cond.colors.contains(c))
                .setDefaultValue(false)
                .setSaveConsumer(v -> {
                    if (v) { if (!cond.colors.contains(c)) cond.colors.add(c); }
                    else cond.colors.remove(c);
                })
                .build());
        }
        return cat.build();
    }

    private static String capitalize(String enumName) {
        String lower = enumName.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static class MutableHexRow { int value; MutableHexRow(int v) { value = v; } }

    /** Raw RGB values, for colors outside the 16 named ones — open-ended, so unlike Formatting/Colors this
     *  genuinely needs an add/remove list rather than pre-populated toggles. Combined with the Colors
     *  category above (a character matches if it's in either list). */
    private static NestedListListEntry<MutableHexRow, MultiElementListEntry<MutableHexRow>> buildHexColorList(
            ConfigEntryBuilder eb, LzText label, List<Integer> source, Consumer<List<Integer>> saveConsumer) {

        List<MutableHexRow> rows = source.stream().map(MutableHexRow::new).collect(Collectors.toCollection(ArrayList::new));

        return new NestedListListEntry<>(
            label.mc(),
            rows,
            true,
            Optional::empty,
            list -> saveConsumer.accept(list.stream().map(r -> r.value).collect(Collectors.toList())),
            ArrayList::new,
            eb.getResetButtonKey(),
            true,
            true,
            (elem, nestedList) -> {
                MutableHexRow row = elem != null ? elem : new MutableHexRow(0xFFFFFF);
                return new MultiElementListEntry<>(
                    LzText.literal("Hex Color " + ruleNumber(nestedList.getValue(), row)).mc(),
                    row,
                    List.of(
                        eb.startColorField(LzText.literal("Color").mc(), row.value)
                            .setDefaultValue(0xFFFFFF)
                            .setSaveConsumer(v -> row.value = v)
                            .build()
                    ),
                    true
                );
            }
        );
    }
}
