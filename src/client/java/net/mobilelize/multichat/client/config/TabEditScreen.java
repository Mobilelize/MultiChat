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
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(LzText.literal("Edit Tab: " + tab.name()).mc())
            .setSavingRunnable(MultichatClient::saveTabsToConfig);

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

        pingsCat.addEntry(eb.startTextDescription(LzText.literal(
                "Tab-specific conditions below are ignored when Use Global is ON.\n" +
                "OR logic: ping fires if any enabled condition is true.").mc()).build());

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
        if (tab.type() == TabType.FILTERED) {
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

            diffCat.addEntry(eb.startTextDescription(LzText.format(
                            """
                                    &eExample&r: &aGuild&r layer &e2&r, &aTrade&r layer &e2&r, &6Other&r layer &e0
                                    &7Guild message matches &aGuild&7 (layer 2, highest) — both &aGuild&7 and &aTrade&7
                                    &7share layer 2, so &aGuild&7 gets it. &6Other&7 at layer 0 never sees it.
                                    &7A message matching neither layer-2 tab falls down to &6Other&7 at layer 0.""").mc())
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

        styleWlCat.addEntry(eb.startTextDescription(LzText.format(
                """
                        &e&lStyle Whitelist&r
                        &7Checks how part of the message actually &frenders&r&7, not its text. &fEmpty list
                        = no restriction.&r Each condition has three small sections:
                          &fFormatting&r&7 — toggle on every style that must be present at once (e.g. bold
                            AND italic).
                          &fColors&r&7 — toggle on the named colors allowed; &fHex Colors&r&7 — add raw RGB
                            values for colors outside the 16 named ones. Every character checked must be ONE
                            of the toggled/added colors, not necessarily the SAME one throughout — so a
                            multi-colored word (different color per letter) can still match, as long as each
                            color it uses is toggled on or listed.
                        Leave Formatting/Colors/Hex Colors empty to not check them. At least one must be set.
                          &fWORD&r&7 — find a specific word and check its style. &fWord Location&r&7 decides
                            where: &fANYWHERE&r&7 searches for the word anywhere in the message, &fSTART&r&7/
                            &fEND&r&7 pin it to the first/last word, &fINDEX&r&7 pins it to an exact position
                            via &fWord Index&r&7 (1 = first word, 2 = second, etc.) — e.g. word &f"Jack"&r&7 +
                            &fSTART&r&7 requires the first word to be "Jack" AND colored red.
                          &fSTART_OF_MESSAGE&r&7 / &fEND_OF_MESSAGE&r&7 — does the message's first/last word
                            have this formatting? Simple boundary check, &fWord&r&7 field is ignored.
                          &7Leave &fWord&r&7 blank to skip content matching and just check formatting at that
                            location instead, without caring what it says (e.g. "is the sender's name italic,"
                            regardless of the actual name). For &fANYWHERE&r&7, blank instead checks if this
                            formatting appears &fanywhere at all&r&7 in the message (at least one character) —
                            useful for click/hover checks, where only a small part (a link, a name) is styled."""
        ).mc()).build());

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

        styleBlCat.addEntry(eb.startTextDescription(LzText.literal(
                "Same shape as Style Whitelist, but excludes a match instead of requiring one.").mc()).build());

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
            // Must be the LAST entry added on this screen: Cloth Config runs every entry's
            // setSaveConsumer in add-order when Save is clicked, and this one needs to run the
            // refilter AFTER every whitelist/blacklist/style-rule save-consumer above has already
            // written its edits to tab.filterRules()/styleRules() — otherwise it scans against the
            // rules as they were before this save, not the ones just changed.
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Refilter on Save").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Rescan all messages and rebuild this tab on save.").mc())
                .setSaveConsumer(v -> { if (v) ChatManager.refilterTab(tab); })
                .build());
        }

        return builder.build();
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
