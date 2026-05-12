package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.MessageRouter;

import java.util.ArrayList;
import java.util.List;

public class TabEditScreen {

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
            .setDefaultValue(TabVisibility.BOTH)
            .setTooltip(Text.literal("Which panel(s) show this tab."))
            .setSaveConsumer(v -> tab.visibility = v)
            .build());

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
            .setTooltip(Text.literal("Prefix prepended to messages when this tab's context is active. E.g. 'msg Fred' or 'w Steve'."))
            .setSaveConsumer(v -> tab.contextPrefix = v.trim())
            .build());

        cat.addEntry(eb.startBooleanToggle(Text.literal("Context Command Mode"), tab.contextCommandMode)
            .setDefaultValue(true)
            .setTooltip(Text.literal("ON: message sent as command (adds /). OFF: sent as chat text."))
            .setSaveConsumer(v -> tab.contextCommandMode = v)
            .build());

        cat.addEntry(eb.startStrList(Text.literal("Whitelist Rules"), serializeRules(tab.filterRules.whitelist))
            .setDefaultValue(List.of())
            .setTooltip(Text.literal("contains:text  |  word:text  |  starts_with:x  |  ends_with:x  |  exact:x  |  regex:pattern  |  sender:name  |  indicator:system  |  all"))
            .setSaveConsumer(list -> {
                tab.filterRules.whitelist.clear();
                tab.filterRules.whitelist.addAll(parseRules(list));
            })
            .build());

        cat.addEntry(eb.startStrList(Text.literal("Blacklist Rules"), serializeRules(tab.filterRules.blacklist))
            .setDefaultValue(List.of())
            .setTooltip(Text.literal("contains:text  |  word:text  |  starts_with:x  |  ends_with:x  |  exact:x  |  regex:pattern  |  sender:name  |  indicator:system  |  all"))
            .setSaveConsumer(list -> {
                tab.filterRules.blacklist.clear();
                tab.filterRules.blacklist.addAll(parseRules(list));
            })
            .build());

        return builder.build();
    }

    private static List<String> serializeRules(List<FilterRule> rules) {
        return rules.stream().map(FilterRule::toString).toList();
    }

    private static List<FilterRule> parseRules(List<String> strings) {
        List<FilterRule> result = new ArrayList<>();
        for (String s : strings) {
            s = s.trim();
            if (s.equalsIgnoreCase("all")) {
                result.add(new FilterRule(FilterType.ALL, ""));
                continue;
            }
            if (s.equalsIgnoreCase("indicator")) {
                result.add(new FilterRule(FilterType.INDICATOR, ""));
                continue;
            }
            int colon = s.indexOf(':');
            if (colon < 0) continue;
            String type  = s.substring(0, colon).trim().toLowerCase();
            String value = s.substring(colon + 1).trim();
            FilterType ft = switch (type) {
                case "contains"     -> FilterType.CONTAINS;
                case "starts_with"  -> FilterType.STARTS_WITH;
                case "ends_with"    -> FilterType.ENDS_WITH;
                case "exact"        -> FilterType.EXACT;
                case "word"         -> FilterType.WORD;
                case "regex"        -> FilterType.REGEX;
                case "sender"       -> FilterType.SENDER;
                default             -> null;
            };
            if (ft == null) continue;
            result.add(new FilterRule(ft, value));
        }
        return result;
    }
}
