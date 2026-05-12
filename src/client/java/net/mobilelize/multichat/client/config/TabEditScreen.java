package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.MultiElementListEntry;
import me.shedaniel.clothconfig2.gui.entries.NestedListListEntry;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.IndicatorFilter;
import net.mobilelize.multichat.client.filter.WhitelistMode;
import net.mobilelize.multichat.client.filter.MessageRouter;

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
            .setTitle(Text.literal("Edit Tab: " + tab.name))
            .setSavingRunnable(() -> {
                MessageRouter.refilterTab(tab);
                MultichatClient.saveTabsToConfig();
            });

        ConfigEntryBuilder eb  = builder.entryBuilder();
        ConfigCategory     cat = builder.getOrCreateCategory(Text.literal("Settings"));

        cat.addEntry(eb.startEnumSelector(Text.literal("Visibility"), TabVisibility.class, tab.visibility)
            .setDefaultValue(tab.type == TabType.FILTERED ? TabVisibility.BOTH : tab.visibility)
            .setTooltip(Text.literal("Which panel(s) show this tab."))
            .setSaveConsumer(v -> tab.visibility = v)
            .build());

        if (tab.type == TabType.FILTERED) {
            cat.addEntry(eb.startBooleanToggle(Text.literal("Collapse Repeating"), tab.collapseRepeating)
                .setDefaultValue(false)
                .setTooltip(Text.literal("Stack identical messages with a counter instead of repeating lines."))
                .setSaveConsumer(v -> tab.collapseRepeating = v)
                .build());

            cat.addEntry(eb.startIntField(Text.literal("Message Cap"), tab.messageCap)
                .setDefaultValue(0)
                .setMin(0)
                .setTooltip(Text.literal("Max messages stored. 0 = unlimited."))
                .setSaveConsumer(v -> tab.messageCap = v)
                .build());
        }

        if (tab.type != TabType.FILTERED) return builder.build();

        cat.addEntry(eb.startBooleanToggle(Text.literal("Difference Mode"), tab.differenceMode)
            .setDefaultValue(false)
            .setTooltip(Text.literal("Show only messages that don't match any other filtered tab."))
            .setSaveConsumer(v -> tab.differenceMode = v)
            .build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Show Badge"), tab.showBadge)
            .setDefaultValue(true)
            .setTooltip(Text.literal("Show unread count badge on this tab."))
            .setSaveConsumer(v -> tab.showBadge = v)
            .build());

        cat.addEntry(eb.startStrField(Text.literal("Context Prefix"), tab.contextPrefix)
            .setDefaultValue("")
            .setTooltip(Text.literal("Prefix prepended when this tab's context is active. E.g. 'msg Fred'."))
            .setSaveConsumer(v -> tab.contextPrefix = v.trim())
            .build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Context Command Mode"), tab.contextCommandMode)
            .setDefaultValue(true)
            .setTooltip(Text.literal("ON: sent as command. OFF: sent as chat text."))
            .setSaveConsumer(v -> tab.contextCommandMode = v)
            .build());

        cat.addEntry(eb.startEnumSelector(Text.literal("Whitelist Mode"), WhitelistMode.class, tab.filterRules.whitelistMode)
            .setDefaultValue(WhitelistMode.ANY)
            .setTooltip(Text.literal("ANY: message matches if any whitelist rule matches. ALL: message must match every whitelist rule."))
            .setSaveConsumer(v -> tab.filterRules.whitelistMode = v)
            .build());

        cat.addEntry(buildRuleList(eb, Text.literal("Whitelist"), tab.filterRules.whitelist, list -> {
            tab.filterRules.whitelist.clear();
            list.forEach(mr -> tab.filterRules.whitelist.add(mr.toFilterRule()));
        }));

        cat.addEntry(buildRuleList(eb, Text.literal("Blacklist"), tab.filterRules.blacklist, list -> {
            tab.filterRules.blacklist.clear();
            list.forEach(mr -> tab.filterRules.blacklist.add(mr.toFilterRule()));
        }));

        return builder.build();
    }

    private static NestedListListEntry<MutableRule, MultiElementListEntry<MutableRule>> buildRuleList(
            ConfigEntryBuilder eb, Text label, List<FilterRule> source, Consumer<List<MutableRule>> saveConsumer) {

        List<MutableRule> rules = source.stream()
            .map(r -> new MutableRule(r.type, r.value, r.indicator))
            .collect(Collectors.toCollection(ArrayList::new));

        return new NestedListListEntry<>(
            label,
            rules,
            true,          // defaultExpanded
            Optional::empty,
            saveConsumer,
            ArrayList::new, // default (empty list)
            eb.getResetButtonKey(),
            true,          // deleteButtonEnabled
            true,          // insertInFront
            (elem, nestedList) -> {
                MutableRule rule = elem != null ? elem : new MutableRule(FilterType.CONTAINS, "", IndicatorFilter.ANY);
                return new MultiElementListEntry<>(
                    Text.literal("Rule"),
                    rule,
                    List.of(
                        eb.startEnumSelector(Text.literal("Type"), FilterType.class, rule.type)
                            .setDefaultValue(FilterType.CONTAINS)
                            .setSaveConsumer(v -> rule.type = v)
                            .build(),
                        eb.startStrField(Text.literal("Value"), rule.value)
                            .setDefaultValue("")
                            .setSaveConsumer(v -> rule.value = v)
                            .build(),
                        eb.startEnumSelector(Text.literal("Indicator"), IndicatorFilter.class, rule.indicator)
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
