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
import net.mobilelize.multichat.client.TabVisibility;
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
            .setTitle(LzText.literal("Edit Tab: " + tab.name).mc())
            .setSavingRunnable(MultichatClient::saveTabsToConfig);

        ConfigEntryBuilder eb  = builder.entryBuilder();
        ConfigCategory     cat = builder.getOrCreateCategory(LzText.literal("Settings").mc());

        if (tab.type == TabType.FILTERED || tab.type == TabType.API) {
            cat.addEntry(eb.startStrField(LzText.literal("Name").mc(), tab.name)
                .setDefaultValue(tab.name)
                .setErrorSupplier(v -> {
                    if (v.trim().isEmpty()) return Optional.of(LzText.literal("Name cannot be empty.").mc());
                    if (tab.apiId == null) {
                        boolean clash = ChatRenderer.TABS.stream()
                            .filter(t -> t != tab && t.type == TabType.FILTERED)
                            .anyMatch(t -> t.name.equalsIgnoreCase(v.trim()));
                        if (clash) return Optional.of(LzText.literal("A tab with that name already exists.").mc());
                    }
                    return Optional.empty();
                })
                .setSaveConsumer(v -> tab.name = v.trim())
                .build());
        }

        cat.addEntry(eb.startEnumSelector(LzText.literal("Visibility").mc(), TabVisibility.class, tab.visibility)
            .setDefaultValue(tab.type == TabType.FILTERED ? TabVisibility.BOTH : tab.visibility)
            .setTooltip(LzText.literal("Which panel(s) show this tab.").mc())
            .setSaveConsumer(v -> tab.visibility = v)
            .build());

        if (tab.type == TabType.FILTERED || tab.type == TabType.API) {
            cat.addEntry(eb.startBooleanToggle(LzText.literal("Show Timestamps").mc(), tab.showTimestamps)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Prepend a timestamp to each message. Format configured in Settings.").mc())
                .setSaveConsumer(v -> tab.showTimestamps = v)
                .build());

            cat.addEntry(eb.startBooleanToggle(LzText.literal("Collapse Repeating").mc(), tab.collapseRepeating)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Stack identical messages with a counter instead of repeating lines.").mc())
                .setSaveConsumer(v -> tab.collapseRepeating = v)
                .build());

            cat.addEntry(eb.startIntField(LzText.literal("Message Cap").mc(), tab.messageCap)
                .setDefaultValue(0)
                .setMin(0)
                .setTooltip(LzText.literal("Max messages stored. 0 = unlimited.").mc())
                .setSaveConsumer(v -> tab.messageCap = v)
                .build());
        }

        if (tab.type != TabType.FILTERED && tab.type != TabType.API) return builder.build();

        if (tab.type == TabType.FILTERED) {
            cat.addEntry(eb.startBooleanToggle(LzText.literal("Refilter on Save").mc(), false)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("When ON, saving will rescan all messages and rebuild this tab.").mc())
                .setSaveConsumer(v -> { if (v) MessageRouter.refilterTab(tab); })
                .build());

            cat.addEntry(eb.startBooleanToggle(LzText.literal("Difference Mode").mc(), tab.differenceMode)
                .setDefaultValue(false)
                .setTooltip(LzText.literal("Show only messages that don't match any other filtered tab.").mc())
                .setSaveConsumer(v -> tab.differenceMode = v)
                .build());
        }

        cat.addEntry(eb.startBooleanToggle(LzText.literal("Show Badge").mc(), tab.showBadge)
            .setDefaultValue(true)
            .setTooltip(LzText.literal("Show unread count badge on this tab.").mc())
            .setSaveConsumer(v -> tab.showBadge = v)
            .build());

        cat.addEntry(eb.startColorField(LzText.literal("Tab Color").mc(), tab.tabColor)
            .setDefaultValue(0)
            .setTooltip(LzText.literal("Custom color for this tab. Set to 000000 (black) to use the default gray.").mc())
            .setSaveConsumer(v -> tab.tabColor = v & 0xFFFFFF)
            .build());

        cat.addEntry(eb.startStrField(LzText.literal("Context Prefix").mc(), tab.contextPrefix)
            .setDefaultValue("")
            .setTooltip(LzText.literal("Prefix prepended when this tab's context is active. E.g. 'msg Fred'.").mc())
            .setSaveConsumer(v -> tab.contextPrefix = v.trim())
            .build());

        cat.addEntry(eb.startBooleanToggle(LzText.literal("Context Command Mode").mc(), tab.contextCommandMode)
            .setDefaultValue(true)
            .setTooltip(LzText.literal("ON: sent as command. OFF: sent as chat text.").mc())
            .setSaveConsumer(v -> tab.contextCommandMode = v)
            .build());

        cat.addEntry(eb.startEnumSelector(LzText.literal("Whitelist Mode").mc(), WhitelistMode.class, tab.filterRules.whitelistMode)
            .setDefaultValue(WhitelistMode.ANY)
            .setTooltip(LzText.literal("ANY: message matches if any whitelist rule matches. ALL: message must match every whitelist rule.").mc())
            .setSaveConsumer(v -> tab.filterRules.whitelistMode = v)
            .build());

        cat.addEntry(buildRuleList(eb, LzText.literal("Whitelist"), tab.filterRules.whitelist, list -> {
            tab.filterRules.whitelist.clear();
            list.forEach(mr -> tab.filterRules.whitelist.add(mr.toFilterRule()));
        }));

        cat.addEntry(buildRuleList(eb, LzText.literal("Blacklist"), tab.filterRules.blacklist, list -> {
            tab.filterRules.blacklist.clear();
            list.forEach(mr -> tab.filterRules.blacklist.add(mr.toFilterRule()));
        }));

        return builder.build();
    }

    private static NestedListListEntry<MutableRule, MultiElementListEntry<MutableRule>> buildRuleList(
            ConfigEntryBuilder eb, LzText label, List<FilterRule> source, Consumer<List<MutableRule>> saveConsumer) {

        List<MutableRule> rules = source.stream()
            .map(r -> new MutableRule(r.type, r.value, r.indicator))
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
