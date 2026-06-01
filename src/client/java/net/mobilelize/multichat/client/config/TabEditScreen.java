package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import net.minecraft.client.gui.screen.Screen;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.WhitelistMode;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;

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

        // ── Settings ──────────────────────────────────────────────────────
        ConfigCategory settings = builder.getOrCreateCategory(LzText.literal("Settings").mc());

        if (tab.type() == TabType.FILTERED || tab.type() == TabType.API) {
            settings.addEntry(eb.startStrField(LzText.literal("Name").mc(), tab.name())
                .setDefaultValue(tab.name())
                .setErrorSupplier(v -> {
                    if (v.trim().isEmpty()) return Optional.of(LzText.literal("Name cannot be empty.").mc());
                    if (tab.apiId() == null) {
                        boolean clash = ChatRenderer.TABS.stream()
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
                .setSaveConsumer(v -> { if (v) MessageRouter.refilterTab(tab); })
                .build());
        }

        if (tab.type() != TabType.FILTERED && tab.type() != TabType.API) return builder.build();

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
                "&eExample&r: &aGuild&r layer &e2&r, &aTrade&r layer &e2&r, &6Other&r layer &e0\n" +
                "&7Guild message matches &aGuild&7 (layer 2, highest) — both &aGuild&7 and &aTrade&7\n" +
                "&7share layer 2, so &aGuild&7 gets it. &6Other&7 at layer 0 never sees it.\n" +
                "&7A message matching neither layer-2 tab falls down to &6Other&7 at layer 0.").mc())
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

        blCat.addEntry(eb.startEnumSelector(LzText.literal("Blacklist Mode").mc(), WhitelistMode.class,
                tab.filterRules().blacklistMode())
            .setDefaultValue(WhitelistMode.ANY)
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
