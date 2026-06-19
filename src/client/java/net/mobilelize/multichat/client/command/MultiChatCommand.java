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
import net.mobilelize.multichat.client.NoteTab;
import net.mobilelize.multichat.client.TabType;
import net.mobilelize.multichat.client.config.ConfigMenu;
import net.mobilelize.multichat.client.config.HelpScreen;
import net.mobilelize.multichat.client.config.NoteEditorScreen;
import net.mobilelize.multichat.client.config.PanelEditScreen;
import net.mobilelize.multichat.client.config.PanelLayoutScreen;
import net.mobilelize.multichat.client.config.PanelListScreen;
import net.mobilelize.multichat.client.config.TabEditScreen;
import net.mobilelize.multichat.client.config.TabListScreen;
import net.mobilelize.multichat.client.quickaction.QuickActionsEditScreen;
import net.mobilelize.multichat.client.quickaction.QuickActionsOverlay;
import net.mobilelize.multichat.client.ChatManager;
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
                    .then(argument("tab", StringArgumentType.greedyString())
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
                        ChatManager.contextTab = null;
                        MultichatClient.saveTabsToConfig();
                        feedback(ctx, "Context cleared.");
                        return 1;
                    })
                    .then(argument("tab", StringArgumentType.word())
                        .suggests(contextSuggestions())
                        .executes(MultiChatCommand::setContext)))
                .then(literal("note")
                    .executes(ctx -> {
                        NoteTab nt = ChatManager.activeNoteTab;
                        if (nt == null) { feedback(ctx, "No active note tab."); return 0; }
                        LzClient.send(() -> LzClient.mc().setScreen(new NoteEditorScreen(null, nt)));
                        return 1;
                    })
                    .then(literal("add")
                        .then(argument("text", StringArgumentType.greedyString())
                            .executes(MultiChatCommand::noteAdd)))
                    .then(literal("open")
                        .then(argument("name", StringArgumentType.greedyString())
                            .suggests(noteSuggestions())
                            .executes(MultiChatCommand::noteOpen))))
                .then(literal("help")
                    .executes(MultiChatCommand::printHelp))));
    }

    private static int noteAdd(CommandContext<FabricClientCommandSource> ctx) {
        NoteTab nt = ChatManager.activeNoteTab;
        if (nt == null) { feedback(ctx, "No active note tab."); return 0; }
        String text = StringArgumentType.getString(ctx, "text");
        nt.addNote(LzText.format(text), ChatRenderer.currentTick);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Note added to [" + nt.name() + "].");
        return 1;
    }

    private static int noteOpen(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        NoteTab nt = (NoteTab) ChatManager.getTabs().stream()
            .filter(t -> t.type() == TabType.NOTES && t.name().equalsIgnoreCase(name))
            .findFirst().orElse(null);
        if (nt == null) { feedback(ctx, "Note tab '" + name + "' not found."); return 0; }
        LzClient.send(() -> LzClient.mc().setScreen(new NoteEditorScreen(null, nt)));
        return 1;
    }

    private static int openPanel(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ChatPanel panel = ChatManager.getAllPanels().stream()
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
        ChatManager.contextTab = tab;
        MultichatClient.saveTabsToConfig();
        String prefix = tab.contextPrefix().isEmpty() ? "(none)" : tab.contextPrefix();
        feedback(ctx, "Context → [" + tab.name() + "] prefix: " + prefix);
        return 1;
    }

    private static int printHelp(CommandContext<FabricClientCommandSource> ctx) {
        LzClient.send(() -> LzClient.mc().setScreen(HelpScreen.create(null)));
        return 1;
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private static SuggestionProvider<FabricClientCommandSource> noteSuggestions() {
        return (ctx, builder) -> {
            for (ChatTab tab : ChatManager.getTabs())
                if (tab.type() == TabType.NOTES
                        && CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), tab.name().toLowerCase()))
                    builder.suggest(tab.name());
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> panelSuggestions() {
        return (ctx, builder) -> {
            for (ChatPanel panel : ChatManager.getAllPanels())
                if (CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), panel.name().toLowerCase()))
                    builder.suggest(panel.name());
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> editSuggestions() {
        return (ctx, builder) -> {
            for (ChatTab tab : ChatManager.getTabs())
                if ((tab.type() == TabType.FILTERED || tab.type() == TabType.API)
                        && CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), tab.name().toLowerCase()))
                    builder.suggest(tab.name());
            return builder.buildFuture();
        };
    }

    private static SuggestionProvider<FabricClientCommandSource> contextSuggestions() {
        return (ctx, builder) -> {
            for (ChatTab tab : ChatManager.getTabs())
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
        for (ChatTab tab : ChatManager.getTabs()) {
            if (!tab.name().equalsIgnoreCase(name)) continue;
            if (tab.type() == TabType.FILTERED) return tab;
            if (tab.type() == TabType.API) api = tab;
        }
        return api;
    }

    private static ChatTab findContextTab(String name) {
        ChatTab api = null;
        for (ChatTab tab : ChatManager.getTabs()) {
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
