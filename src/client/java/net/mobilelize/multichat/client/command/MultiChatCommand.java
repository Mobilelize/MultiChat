package net.mobilelize.multichat.client.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.TabVisibility;
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MultiChatCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = literal("multichat")
                .then(literal("tab")
                    .then(literal("add")
                        .then(argument("name", StringArgumentType.word())
                        .executes(MultiChatCommand::tabAdd)))
                    .then(literal("remove")
                        .then(argument("name", StringArgumentType.word())
                        .executes(MultiChatCommand::tabRemove)))
                    .then(literal("list")
                        .executes(MultiChatCommand::tabList))
                    .then(literal("difference")
                        .then(argument("name", StringArgumentType.word())
                        .then(argument("enabled", BoolArgumentType.bool())
                        .executes(MultiChatCommand::tabDifference))))
                    .then(literal("visibility")
                        .then(argument("name", StringArgumentType.word())
                        .then(argument("visibility", StringArgumentType.word())
                        .executes(MultiChatCommand::tabVisibility)))))
                .then(literal("toggle")
                    .then(literal("left").executes(ctx -> toggleContext(ctx, ChatRenderer.LEFT)))
                    .then(literal("right").executes(ctx -> toggleContext(ctx, ChatRenderer.RIGHT))))
                .then(literal("filter")
                    .then(literal("add")
                        .then(argument("tab", StringArgumentType.word())
                        .then(argument("list", StringArgumentType.word())
                        .then(argument("type", StringArgumentType.word())
                        .then(argument("value", StringArgumentType.greedyString())
                        .executes(MultiChatCommand::filterAdd))))))
                    .then(literal("remove")
                        .then(argument("tab", StringArgumentType.word())
                        .then(argument("list", StringArgumentType.word())
                        .then(argument("index", IntegerArgumentType.integer(0))
                        .executes(MultiChatCommand::filterRemove)))))
                    .then(literal("list")
                        .then(argument("tab", StringArgumentType.word())
                        .executes(MultiChatCommand::filterList)))
                    .then(literal("clear")
                        .then(argument("tab", StringArgumentType.word())
                        .then(argument("list", StringArgumentType.word())
                        .executes(MultiChatCommand::filterClear)))));

            dispatcher.register(root);
            dispatcher.register(literal("mc").redirect(root.build()));
        });
    }

    // ------------------------------------------------------------------
    // Tab commands
    // ------------------------------------------------------------------

    private static int tabAdd(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        if (findTab(name) != null) {
            feedback(ctx, "Tab '" + name + "' already exists.");
            return 0;
        }

        ChatTab tab = new ChatTab(name, TabType.FILTERED, TabVisibility.BOTH);
        ChatRenderer.TABS.add(tab);
        MessageRouter.refilterTab(tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Created tab '" + name + "' (visible: both).");
        return 1;
    }

    private static int tabRemove(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");

        ChatTab tab = findTab(name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Cannot remove built-in tab."); return 0; }

        ChatRenderer.TABS.remove(tab);
        for (var panel : List.of(ChatRenderer.LEFT, ChatRenderer.RIGHT)) {
            if (panel.activeTab >= panel.getVisibleTabs().size()) panel.activeTab = 0;
        }

        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Removed tab '" + name + "'.");
        return 1;
    }

    private static int tabList(CommandContext<FabricClientCommandSource> ctx) {
        StringBuilder sb = new StringBuilder("Tabs:");
        for (int i = 0; i < ChatRenderer.TABS.size(); i++) {
            ChatTab t = ChatRenderer.TABS.get(i);
            String diff = t.differenceMode ? " [diff]" : "";
            sb.append("\n  [").append(i).append("] ").append(t.name)
              .append(" (").append(t.type.name().toLowerCase()).append(")")
              .append(" [").append(t.visibility.name().toLowerCase()).append("]")
              .append(diff);
        }
        feedback(ctx, sb.toString());
        return 1;
    }

    private static int tabDifference(CommandContext<FabricClientCommandSource> ctx) {
        String name    = StringArgumentType.getString(ctx, "name");
        boolean enable = BoolArgumentType.getBool(ctx, "enabled");

        ChatTab tab = findTab(name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Only FILTERED tabs support difference mode."); return 0; }

        tab.differenceMode = enable;
        MessageRouter.refilterTab(tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Difference mode " + (enable ? "enabled" : "disabled") + " on '" + name + "'.");
        return 1;
    }

    private static int tabVisibility(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String vis  = StringArgumentType.getString(ctx, "visibility").toLowerCase();

        ChatTab tab = findTab(name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Cannot change visibility of built-in tabs."); return 0; }

        TabVisibility visibility = switch (vis) {
            case "left"  -> TabVisibility.LEFT;
            case "right" -> TabVisibility.RIGHT;
            case "both"  -> TabVisibility.BOTH;
            case "none"  -> TabVisibility.NONE;
            default -> null;
        };
        if (visibility == null) {
            feedback(ctx, "Unknown visibility '" + vis + "'. Use: left, right, both, none.");
            return 0;
        }

        tab.visibility = visibility;
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Tab '" + name + "' visibility set to " + vis + ".");
        return 1;
    }

    // ------------------------------------------------------------------
    // Filter commands
    // ------------------------------------------------------------------

    private static int filterAdd(CommandContext<FabricClientCommandSource> ctx) {
        String tabName  = StringArgumentType.getString(ctx, "tab");
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        String typeName = StringArgumentType.getString(ctx, "type").toLowerCase();
        String value    = StringArgumentType.getString(ctx, "value");

        ChatTab tab = findTab(tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Built-in tabs cannot have filter rules."); return 0; }

        FilterType ft = parseFilterType(typeName);
        if (ft == null) { feedback(ctx, "Unknown type '" + typeName + "'. Use: all, contains, starts_with, ends_with, exact, word, regex, sender, indicator."); return 0; }

        FilterRule rule = new FilterRule(ft, value);

        List<FilterRule> list = listName.equals("blacklist") ? tab.filterRules.blacklist : tab.filterRules.whitelist;
        list.add(rule);

        MessageRouter.refilterTab(tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Added " + listName + " rule to '" + tabName + "': " + rule);
        return 1;
    }

    private static int filterRemove(CommandContext<FabricClientCommandSource> ctx) {
        String tabName  = StringArgumentType.getString(ctx, "tab");
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        int    index    = IntegerArgumentType.getInteger(ctx, "index");

        ChatTab tab = findTab(tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }

        List<FilterRule> list = listName.equals("blacklist") ? tab.filterRules.blacklist : tab.filterRules.whitelist;
        if (index >= list.size()) { feedback(ctx, "Index " + index + " out of range (size: " + list.size() + ")."); return 0; }

        FilterRule removed = list.remove(index);
        MessageRouter.refilterTab(tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Removed rule [" + index + "] from '" + tabName + "' " + listName + ": " + removed);
        return 1;
    }

    private static int filterList(CommandContext<FabricClientCommandSource> ctx) {
        String tabName = StringArgumentType.getString(ctx, "tab");

        ChatTab tab = findTab(tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Built-in tabs have no filter rules."); return 0; }

        StringBuilder sb = new StringBuilder("Filters for '" + tabName + "':");
        sb.append("\n  Whitelist:");
        if (tab.filterRules.whitelist.isEmpty()) sb.append(" (empty — passes all)");
        for (int i = 0; i < tab.filterRules.whitelist.size(); i++)
            sb.append("\n    [").append(i).append("] ").append(tab.filterRules.whitelist.get(i));
        sb.append("\n  Blacklist:");
        if (tab.filterRules.blacklist.isEmpty()) sb.append(" (empty — blocks nothing)");
        for (int i = 0; i < tab.filterRules.blacklist.size(); i++)
            sb.append("\n    [").append(i).append("] ").append(tab.filterRules.blacklist.get(i));

        feedback(ctx, sb.toString());
        return 1;
    }

    private static int filterClear(CommandContext<FabricClientCommandSource> ctx) {
        String tabName  = StringArgumentType.getString(ctx, "tab");
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();

        ChatTab tab = findTab(tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Built-in tabs have no filter rules."); return 0; }

        List<FilterRule> list = listName.equals("blacklist") ? tab.filterRules.blacklist : tab.filterRules.whitelist;
        list.clear();
        MessageRouter.refilterTab(tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Cleared " + listName + " on '" + tabName + "'.");
        return 1;
    }

    // ------------------------------------------------------------------
    // Toggle context command
    // ------------------------------------------------------------------

    private static int toggleContext(CommandContext<FabricClientCommandSource> ctx, ChatPanel panel) {
        ChatTab active = panel.getActiveTab();
        if (active == null) { feedback(ctx, "No active tab."); return 0; }
        if (ChatRenderer.contextTab == active) {
            ChatRenderer.contextTab = null;
            MultichatClient.saveTabsToConfig();
            feedback(ctx, "Context off.");
        } else {
            ChatRenderer.contextTab = active;
            MultichatClient.saveTabsToConfig();
            String prefix = active.contextPrefix.isEmpty() ? "(none)" : active.contextPrefix;
            feedback(ctx, "Context on [" + active.name + "] prefix: " + prefix);
        }
        return 1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ChatTab findTab(String name) {
        return ChatRenderer.TABS.stream().filter(t -> t.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Text.literal("[MultiChat] " + msg));
    }

    public static FilterType parseFilterType(String s) {
        return switch (s) {
            case "all"          -> FilterType.ALL;
            case "contains"     -> FilterType.CONTAINS;
            case "starts_with"  -> FilterType.STARTS_WITH;
            case "ends_with"    -> FilterType.ENDS_WITH;
            case "exact"        -> FilterType.EXACT;
            case "word"         -> FilterType.WORD;
            case "regex"        -> FilterType.REGEX;
            case "sender"       -> FilterType.SENDER;
            case "indicator"    -> FilterType.INDICATOR;
            default             -> null;
        };
    }

}
