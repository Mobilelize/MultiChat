package net.mobilelize.multichat.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.client.ChatPanel;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.config.ConfigMenu;
import net.mobilelize.multichat.client.config.PanelEditScreen;
import net.mobilelize.multichat.client.config.PanelLayoutScreen;
import net.mobilelize.multichat.client.config.PanelListScreen;
import net.mobilelize.multichat.client.config.TabEditScreen;
import net.mobilelize.multichat.client.config.TabListScreen;
import net.mobilelize.multichat.client.quickaction.QuickActionsEditScreen;
import net.mobilelize.multichat.client.quickaction.QuickActionsOverlay;
import net.mobilelize.multichat.client.render.ChatRenderer;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MultiChatCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("multichat")
                .executes(ctx -> {
                    LzClient.send(() -> LzClient.mc().setScreen(new TabListScreen(null)));
                    return 1;
                })
                .then(literal("edit")
                    .then(argument("tab", StringArgumentType.word())
                        .suggests(editSuggestions())
                        .executes(MultiChatCommand::editTab)))
                .then(literal("panels")
                    .executes(ctx -> {
                        LzClient.send(() -> LzClient.mc().setScreen(new PanelListScreen(null)));
                        return 1;
                    })
                    .then(argument("name", StringArgumentType.greedyString())
                        .suggests(panelSuggestions())
                        .executes(MultiChatCommand::openPanel)))
                .then(literal("layout")
                    .executes(ctx -> {
                        LzClient.send(() -> LzClient.mc().setScreen(new PanelLayoutScreen(null)));
                        return 1;
                    }))
                .then(literal("settings")
                    .executes(ctx -> {
                        LzClient.send(() -> LzClient.mc().setScreen(ConfigMenu.create(null)));
                        return 1;
                    }))
                .then(literal("quickactions")
                    .executes(ctx -> {
                        LzClient.send(() -> LzClient.mc().setScreen(new QuickActionsOverlay(null, "")));
                        return 1;
                    })
                    .then(literal("edit")
                        .executes(ctx -> {
                            LzClient.send(() -> LzClient.mc().setScreen(new QuickActionsEditScreen(null)));
                            return 1;
                        })))
                .then(literal("context")
                    .executes(ctx -> {
                        ChatRenderer.contextTab = null;
                        MultichatClient.saveTabsToConfig();
                        feedback(ctx, "Context cleared.");
                        return 1;
                    })
                    .then(argument("tab", StringArgumentType.word())
                        .suggests(contextSuggestions())
                        .executes(MultiChatCommand::setContext)))));
    }

    private static int openPanel(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ChatPanel panel = ChatRenderer.PANELS.stream()
            .filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
        if (panel == null) { feedback(ctx, "Panel '" + name + "' not found."); return 0; }
        LzClient.send(() -> LzClient.mc().setScreen(PanelEditScreen.create(null, panel)));
        return 1;
    }

    private static int editTab(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "tab");
        ChatTab tab = findEditableTab(name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        LzClient.send(() -> LzClient.mc().setScreen(TabEditScreen.create(null, tab)));
        return 1;
    }

    private static int setContext(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "tab");
        ChatTab tab = findContextTab(name);
        if (tab == null) { feedback(ctx, "Tab '" + name + "' not found."); return 0; }
        ChatRenderer.contextTab = tab;
        MultichatClient.saveTabsToConfig();
        String prefix = tab.contextPrefix().isEmpty() ? "(none)" : tab.contextPrefix();
        feedback(ctx, "Context → [" + tab.name() + "] prefix: " + prefix);
        return 1;
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private static SuggestionProvider<FabricClientCommandSource> panelSuggestions() {
        return (ctx, builder) -> {
            for (ChatPanel panel : ChatRenderer.PANELS)
                if (CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), panel.name().toLowerCase()))
                    builder.suggest(panel.name());
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> editSuggestions() {
        return (ctx, builder) -> {
            for (ChatTab tab : ChatRenderer.TABS)
                if ((tab.type() == TabType.FILTERED || tab.type() == TabType.API)
                        && CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), tab.name().toLowerCase()))
                    builder.suggest(tab.name());
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> contextSuggestions() {
        return (ctx, builder) -> {
            for (ChatTab tab : ChatRenderer.TABS)
                if ((tab.type() == TabType.FILTERED || tab.type() == TabType.API)
                        && CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), tab.name().toLowerCase()))
                    builder.suggest(tab.name());
            return builder.buildFuture();
        };
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** FILTERED tab wins over API if same name. */
    private static ChatTab findEditableTab(String name) {
        ChatTab api = null;
        for (ChatTab tab : ChatRenderer.TABS) {
            if (!tab.name().equalsIgnoreCase(name)) continue;
            if (tab.type() == TabType.FILTERED) return tab;
            if (tab.type() == TabType.API) api = tab;
        }
        return api;
    }

    private static ChatTab findContextTab(String name) {
        ChatTab api = null;
        for (ChatTab tab : ChatRenderer.TABS) {
            if (!tab.name().equalsIgnoreCase(name)) continue;
            if (tab.type() == TabType.FILTERED) return tab;
            if (tab.type() == TabType.API) api = tab;
        }
        return api;
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(LzText.literal("[MultiChat] " + msg).mc());
    }
}
