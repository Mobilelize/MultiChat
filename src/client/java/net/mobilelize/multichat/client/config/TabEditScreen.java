package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.client.LzSound;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.filter.*;
import net.mobilelize.multichat.client.ping.PingPlayer;

import java.util.ArrayList;
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
                        boolean clash = ChatManager.getTabs().stream()
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
            settings.addEntry(eb.startBooleanToggle(LzText.literal("Refilter on Save").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Rescan all messages and rebuild this tab on save.").mc())
                .setSaveConsumer(v -> { if (v) ChatManager.refilterTab(tab); })
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
                    LzText.literal("Rule").mc(),
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
}
