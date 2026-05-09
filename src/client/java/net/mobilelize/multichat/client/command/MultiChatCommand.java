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
import net.mobilelize.multichat.client.filter.FilterRule;
import net.mobilelize.multichat.client.filter.FilterType;
import net.mobilelize.multichat.client.filter.MessageRouter;
import net.mobilelize.multichat.client.filter.MessageType;
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
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                        .executes(MultiChatCommand::tabAdd))))
                    .then(literal("remove")
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                        .executes(MultiChatCommand::tabRemove))))
                    .then(literal("list")
                        .then(argument("panel", StringArgumentType.word())
                        .executes(MultiChatCommand::tabList)))
                    .then(literal("difference")
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.word())
                        .then(argument("enabled", BoolArgumentType.bool())
                        .executes(MultiChatCommand::tabDifference))))))
                .then(literal("filter")
                    .then(literal("add")
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("tab", StringArgumentType.word())
                        .then(argument("list", StringArgumentType.word())
                        .then(argument("type", StringArgumentType.word())
                        .then(argument("value", StringArgumentType.greedyString())
                        .executes(MultiChatCommand::filterAdd)))))))
                    .then(literal("remove")
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("tab", StringArgumentType.word())
                        .then(argument("list", StringArgumentType.word())
                        .then(argument("index", IntegerArgumentType.integer(0))
                        .executes(MultiChatCommand::filterRemove))))))
                    .then(literal("list")
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("tab", StringArgumentType.word())
                        .executes(MultiChatCommand::filterList))))
                    .then(literal("clear")
                        .then(argument("panel", StringArgumentType.word())
                        .then(argument("tab", StringArgumentType.word())
                        .then(argument("list", StringArgumentType.word())
                        .executes(MultiChatCommand::filterClear))))));

            dispatcher.register(root);
            dispatcher.register(literal("mc").redirect(root.build()));
        });
    }

    // ------------------------------------------------------------------
    // Tab commands
    // ------------------------------------------------------------------

    private static int tabAdd(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String name = StringArgumentType.getString(ctx, "name");

        if (findTab(panel, name) != null) {
            feedback(ctx, "Tab '" + name + "' already exists.");
            return 0;
        }

        ChatTab tab = new ChatTab(name, TabType.FILTERED);
        panel.tabs.add(tab);
        MessageRouter.refilterTab(panel, tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Created tab '" + name + "' on " + panelName(panel) + ".");
        return 1;
    }

    private static int tabRemove(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String name = StringArgumentType.getString(ctx, "name");

        ChatTab tab = findTab(panel, name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Cannot remove built-in tab."); return 0; }

        int idx = panel.tabs.indexOf(tab);
        panel.tabs.remove(tab);
        if (panel.activeTab >= panel.tabs.size()) panel.activeTab = 0;
        else if (panel.activeTab > idx) panel.activeTab--;

        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Removed tab '" + name + "' from " + panelName(panel) + ".");
        return 1;
    }

    private static int tabList(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;

        StringBuilder sb = new StringBuilder(panelName(panel) + " tabs:");
        for (int i = 0; i < panel.tabs.size(); i++) {
            ChatTab t = panel.tabs.get(i);
            String marker = (i == panel.activeTab) ? " *" : "";
            String diff   = t.differenceMode ? " [diff]" : "";
            sb.append("\n  [").append(i).append("] ").append(t.name)
              .append(" (").append(t.type.name().toLowerCase()).append(")").append(diff).append(marker);
        }
        feedback(ctx, sb.toString());
        return 1;
    }

    private static int tabDifference(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String name    = StringArgumentType.getString(ctx, "name");
        boolean enable = BoolArgumentType.getBool(ctx, "enabled");

        ChatTab tab = findTab(panel, name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Only FILTERED tabs support difference mode."); return 0; }

        tab.differenceMode = enable;
        MessageRouter.refilterTab(panel, tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Difference mode " + (enable ? "enabled" : "disabled") + " on '" + name + "'.");
        return 1;
    }

    // ------------------------------------------------------------------
    // Filter commands
    // ------------------------------------------------------------------

    private static int filterAdd(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String tabName  = StringArgumentType.getString(ctx, "tab");
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        String typeName = StringArgumentType.getString(ctx, "type").toLowerCase();
        String value    = StringArgumentType.getString(ctx, "value");

        ChatTab tab = findTab(panel, tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Built-in tabs cannot have filter rules."); return 0; }

        FilterType ft = parseFilterType(typeName);
        if (ft == null) { feedback(ctx, "Unknown type '" + typeName + "'. Use: all, keyword, regex, sender, message_type."); return 0; }

        FilterRule rule;
        if (ft == FilterType.MESSAGE_TYPE) {
            MessageType mt = parseMessageType(value);
            if (mt == null) { feedback(ctx, "Unknown message type '" + value + "'."); return 0; }
            rule = new FilterRule(ft, value, mt);
        } else {
            rule = new FilterRule(ft, value);
        }

        List<FilterRule> list = listName.equals("blacklist") ? tab.filterRules.blacklist : tab.filterRules.whitelist;
        list.add(rule);

        MessageRouter.refilterTab(panel, tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Added " + listName + " rule to '" + tabName + "': " + rule);
        return 1;
    }

    private static int filterRemove(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String tabName  = StringArgumentType.getString(ctx, "tab");
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        int    index    = IntegerArgumentType.getInteger(ctx, "index");

        ChatTab tab = findTab(panel, tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }

        List<FilterRule> list = listName.equals("blacklist") ? tab.filterRules.blacklist : tab.filterRules.whitelist;
        if (index >= list.size()) { feedback(ctx, "Index " + index + " out of range (size: " + list.size() + ")."); return 0; }

        FilterRule removed = list.remove(index);
        MessageRouter.refilterTab(panel, tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Removed rule [" + index + "] from '" + tabName + "' " + listName + ": " + removed);
        return 1;
    }

    private static int filterList(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String tabName = StringArgumentType.getString(ctx, "tab");

        ChatTab tab = findTab(panel, tabName);
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
        ChatPanel panel = getPanel(ctx, "panel");
        if (panel == null) return 0;
        String tabName  = StringArgumentType.getString(ctx, "tab");
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();

        ChatTab tab = findTab(panel, tabName);
        if (tab == null) { feedback(ctx, "Tab '" + tabName + "' not found."); return 0; }
        if (tab.type != TabType.FILTERED) { feedback(ctx, "Built-in tabs have no filter rules."); return 0; }

        List<FilterRule> list = listName.equals("blacklist") ? tab.filterRules.blacklist : tab.filterRules.whitelist;
        list.clear();
        MessageRouter.refilterTab(panel, tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Cleared " + listName + " on '" + tabName + "'.");
        return 1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ChatPanel getPanel(CommandContext<FabricClientCommandSource> ctx, String argName) {
        String name = StringArgumentType.getString(ctx, argName).toLowerCase();
        return switch (name) {
            case "left"  -> ChatRenderer.PANELS.get(0);
            case "right" -> ChatRenderer.PANELS.get(1);
            default -> { feedback(ctx, "Unknown panel '" + name + "'. Use: left, right."); yield null; }
        };
    }

    private static ChatTab findTab(ChatPanel panel, String name) {
        return panel.tabs.stream().filter(t -> t.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private static String panelName(ChatPanel panel) {
        return panel == ChatRenderer.PANELS.get(0) ? "left" : "right";
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Text.literal("[MultiChat] " + msg));
    }

    public static FilterType parseFilterType(String s) {
        return switch (s) {
            case "all"          -> FilterType.ALL;
            case "keyword"      -> FilterType.KEYWORD;
            case "regex"        -> FilterType.REGEX;
            case "sender"       -> FilterType.SENDER_NAME;
            case "message_type" -> FilterType.MESSAGE_TYPE;
            default             -> null;
        };
    }

    public static MessageType parseMessageType(String s) {
        try { return MessageType.valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
    }
}
