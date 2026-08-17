package net.mobilelize.multichat.client.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandSource;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzClient;
import net.mobilelize.fabriclib.render.LzDraw;
import net.mobilelize.fabriclib.text.LzText;
import net.mobilelize.multichat.api.TabActionCallback;
import net.mobilelize.multichat.client.*;
import net.mobilelize.multichat.client.config.*;
import net.mobilelize.multichat.client.quickaction.QuickActionsEditScreen;
import net.mobilelize.multichat.client.quickaction.QuickActionsOverlay;
import net.mobilelize.multichat.client.render.ChatRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class MultiChatCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = dispatcher.register(literal("multichat")
                .executes(ctx -> {
                    LzClient.send(() -> LzClient.mc().setScreen(new TabListScreen(null)));
                    return 1;
                })
                .then(literal("tab")
                    .executes(ctx -> {
                        LzClient.send(() -> LzClient.mc().setScreen(new TabListScreen(null)));
                        return 1;
                    })
                    .then(literal("add")
                        .executes(MultiChatCommand::addTab)
                        .then(argument("name", StringArgumentType.greedyString())
                            .executes(MultiChatCommand::addNamedTab)))
                    .then(literal("remove")
                        .executes(MultiChatCommand::removeActiveTab))
                    .then(literal("edit")
                        .then(argument("tab", StringArgumentType.greedyString())
                            .suggests(editSuggestions())
                            .executes(MultiChatCommand::editTab))))
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
                .then(literal("highlight")
                    .executes(ctx -> {
                        LzClient.send(() -> LzClient.mc().setScreen(ConfigMenu.create(null)));
                        return 1;
                    })
                    .then(literal("add")
                        .then(argument("word", StringArgumentType.greedyString())
                            .executes(MultiChatCommand::highlightKeywordAdd)))
                    .then(literal("remove")
                        .then(argument("word", StringArgumentType.greedyString())
                            .suggests(highlightSuggestions(cfg -> cfg.userHighlightKeywords))
                            .executes(MultiChatCommand::highlightKeywordRemove)))
                    .then(literal("exception")
                        .then(literal("add")
                            .then(argument("word", StringArgumentType.greedyString())
                                .executes(MultiChatCommand::highlightExceptionAdd)))
                        .then(literal("remove")
                            .then(argument("word", StringArgumentType.greedyString())
                                .suggests(highlightSuggestions(cfg -> cfg.highlightExceptions))
                                .executes(MultiChatCommand::highlightExceptionRemove))))
                    .then(literal("api")
                        .then(literal("exception")
                            .then(literal("add")
                                .then(argument("word", StringArgumentType.greedyString())
                                    .executes(MultiChatCommand::highlightApiExceptionAdd)))
                            .then(literal("remove")
                                .then(argument("word", StringArgumentType.greedyString())
                                    .suggests(highlightSuggestions(cfg -> cfg.apiHighlightExceptions))
                                    .executes(MultiChatCommand::highlightApiExceptionRemove))))))
                .then(literal("info")
                    .then(literal("hide")
                        .executes(MultiChatCommand::infoHide))
                    .then(literal("show")
                        .executes(MultiChatCommand::infoShow))
                    .then(literal("panel")
                        .then(literal("hide")
                            .executes(MultiChatCommand::infoPanelHide))
                        .then(literal("show")
                            .executes(MultiChatCommand::infoPanelShow))))
                .then(literal("execute")
                    .then(argument("id", StringArgumentType.word())
                        .executes(MultiChatCommand::executeAction)))
                .then(literal("debug")
                    // Live predicate, not a registration-time check — toggling the "Debug Commands"
                    // setting hides/reveals this branch (tab-completion included) immediately, no
                    // command-tree rebuild needed.
                    .requires(source -> MultichatClient.CONFIG.debugCommandsEnabled)
                    .then(literal("fill")
                        .then(argument("count", IntegerArgumentType.integer(1, 5_000_000))
                            .executes(MultiChatCommand::debugFill)))
                    .then(literal("clear")
                        .executes(MultiChatCommand::debugClear)))
                .then(literal("help")
                    .executes(MultiChatCommand::printHelp)));
            dispatcher.register(literal("mlchat")
                .executes(ctx -> {
                    LzClient.send(() -> LzClient.mc().setScreen(new TabListScreen(null)));
                    return 1;
                })
                .redirect(root));
        });
    }

    private static int highlightKeywordAdd(CommandContext<FabricClientCommandSource> ctx) {
        return addToList(ctx, "keyword", MultichatClient.CONFIG.userHighlightKeywords);
    }

    private static int highlightKeywordRemove(CommandContext<FabricClientCommandSource> ctx) {
        return removeFromList(ctx, "keyword", MultichatClient.CONFIG.userHighlightKeywords);
    }

    private static int highlightExceptionAdd(CommandContext<FabricClientCommandSource> ctx) {
        return addToList(ctx, "exception", MultichatClient.CONFIG.highlightExceptions);
    }

    private static int highlightExceptionRemove(CommandContext<FabricClientCommandSource> ctx) {
        return removeFromList(ctx, "exception", MultichatClient.CONFIG.highlightExceptions);
    }

    private static int highlightApiExceptionAdd(CommandContext<FabricClientCommandSource> ctx) {
        return addToList(ctx, "API exception", MultichatClient.CONFIG.apiHighlightExceptions);
    }

    private static int highlightApiExceptionRemove(CommandContext<FabricClientCommandSource> ctx) {
        return removeFromList(ctx, "API exception", MultichatClient.CONFIG.apiHighlightExceptions);
    }

    private static int addToList(CommandContext<FabricClientCommandSource> ctx, String label, List<String> list) {
        String word = StringArgumentType.getString(ctx, "word");
        if (list.stream().anyMatch(w -> w.equalsIgnoreCase(word))) {
            feedback(ctx, "Already have " + label + " '" + word + "'."); return 0;
        }
        list.add(word);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Added " + label + " [" + word + "].");
        return 1;
    }

    private static int removeFromList(CommandContext<FabricClientCommandSource> ctx, String label, List<String> list) {
        String word = StringArgumentType.getString(ctx, "word");
        if (!list.removeIf(w -> w.equalsIgnoreCase(word))) {
            feedback(ctx, "No " + label + " '" + word + "'."); return 0;
        }
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Removed " + label + " [" + word + "].");
        return 1;
    }

    private static SuggestionProvider<FabricClientCommandSource> highlightSuggestions(Function<MultiChatConfig, List<String>> listGetter) {
        return (ctx, builder) -> {
            for (String w : listGetter.apply(MultichatClient.CONFIG))
                if (CommandSource.shouldSuggest(builder.getRemaining().toLowerCase(), w.toLowerCase()))
                    builder.suggest(w);
            return builder.buildFuture();
        };
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

    private static int addTab(CommandContext<FabricClientCommandSource> ctx) {
        List<String> existing = ChatManager.getTabs().stream().map(t -> t.name().toLowerCase()).toList();
        int n = 1;
        while (existing.contains("tab " + n)) n++;
        String name = "Tab " + n;
        ChatManager.getTabs().add(new ChatTab(name, TabType.FILTERED, List.of(0, 1)));
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Added tab [" + name + "].");
        return 1;
    }

    private static int addNamedTab(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (ChatManager.getTabs().stream().anyMatch(t -> t.type() == TabType.FILTERED && t.name().equalsIgnoreCase(name))) {
            feedback(ctx, "Filtered tab '" + name + "' already exists."); return 0;
        }
        ChatManager.getTabs().add(new ChatTab(name, TabType.FILTERED, List.of(0, 1)));
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Added tab [" + name + "].");
        return 1;
    }

    private static int removeActiveTab(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = ChatManager.focusedPanel;
        if (panel == null) { feedback(ctx, "No focused panel."); return 0; }
        ChatTab tab = panel.getActiveTab();
        if (tab == null) { feedback(ctx, "No active tab."); return 0; }
        boolean apiDeletable = tab.type() == TabType.API && tab.playerDeletable();
        if (tab.type() != TabType.FILTERED && tab.type() != TabType.NOTES && tab.type() != TabType.SEARCH && !apiDeletable) {
            feedback(ctx, "Cannot remove tab [" + tab.name() + "]."); return 0;
        }
        ChatManager.getTabs().remove(tab);
        if (tab.type() == TabType.NOTES && ChatManager.activeNoteTab == tab) {
            ChatManager.activeNoteTab = (NoteTab) ChatManager.getTabs().stream()
                .filter(t -> t.type() == TabType.NOTES).findFirst().orElse(null);
        }
        if (tab.type() == TabType.API) net.mobilelize.multichat.api.MultiChatApi.notifyTabRemoved(tab);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Removed tab [" + tab.name() + "].");
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

    private static int executeAction(CommandContext<FabricClientCommandSource> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        TabActionCallback.EVENT.invoker().onAction(id);
        return 1;
    }

    private static int infoShow(CommandContext<FabricClientCommandSource> ctx) {
        InfoTab it = (InfoTab) ChatManager.getTabs().stream()
            .filter(t -> t instanceof InfoTab).findFirst().orElse(null);
        if (it == null) { feedback(ctx, "Info tab not found."); return 0; }
        it.visibleOnPanels().add(1);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Info tab shown on Panel 2.");
        return 1;
    }

    private static int infoHide(CommandContext<FabricClientCommandSource> ctx) {
        InfoTab it = (InfoTab) ChatManager.getTabs().stream()
            .filter(t -> t instanceof InfoTab).findFirst().orElse(null);
        if (it == null) { feedback(ctx, "Info tab not found."); return 0; }
        it.visibleOnPanels().remove(1);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Info tab hidden from Panel 2.");
        return 1;
    }

    private static int infoPanelHide(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = ChatManager.getAllPanels().stream().filter(p -> p.id() == 1).findFirst().orElse(null);
        if (panel == null) { feedback(ctx, "Panel 2 not found."); return 0; }
        panel.setEnabled(false);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Panel 2 hidden.");
        return 1;
    }

    private static int infoPanelShow(CommandContext<FabricClientCommandSource> ctx) {
        ChatPanel panel = ChatManager.getAllPanels().stream().filter(p -> p.id() == 1).findFirst().orElse(null);
        if (panel == null) { feedback(ctx, "Panel 2 not found."); return 0; }
        panel.setEnabled(true);
        MultichatClient.saveTabsToConfig();
        feedback(ctx, "Panel 2 shown.");
        return 1;
    }

    /** Bulk-generates {@code count} synthetic messages straight into the All tab's message list for
     *  perf testing — bypasses live routing/wrap entirely (a single List.addAll(0, ...) instead of
     *  count individual addFirst() + per-message wrap calls) since the point is to get a huge history
     *  to test against quickly, not to simulate count individual chat events arriving one at a time.
     *  Tagged with a recognizable "Test message #" prefix so `debug clear` can find and remove them
     *  again without touching any real chat history mixed in. resetForRefresh() at the end is what
     *  actually exercises the offload path (Optimized Loading's skeleton/background wrap) against the
     *  new huge tab — that's the thing this command exists to make easy to test. */
    private static int debugFill(CommandContext<FabricClientCommandSource> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        ChatTab allTab = ChatManager.getTabs().stream().filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (allTab == null) { feedback(ctx, "No All tab found."); return 0; }

        long start = System.currentTimeMillis();
        int baseTick = ChatRenderer.currentTick;
        List<ChatTab.MessageEntry> synthetic = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            LzText text = LzText.literal("Test message #" + i + " — the quick brown fox jumps over the lazy dog");
            LzChatEntry entry = new LzChatEntry(baseTick - i, text, null);
            synthetic.add(new ChatTab.MessageEntry(text, text, entry));
            // Debug-only: this text was never rendered anywhere before now, so none of its glyphs
            // are baked yet. The rebuild resetForRefresh() triggers below can run on
            // WrapCoordinator's background thread (see ChatPanel.rebuildTabLines), and lazy
            // glyph-atlas baking touches GL state — safe on the main thread we're already on right
            // here, an outright crash if it happens to fire on that background thread instead.
            // Warming it here, once, up front, is exactly the dedicated place for this: real chat
            // text doesn't need it (it's already rendered live before any rebuild ever sees it), so
            // this has no business living in the general rebuild path.
            LzDraw.textWidth(text);
        }
        allTab.messages().addAll(0, synthetic); // index 0 = newest — prepend the whole batch as one shift
        ChatManager.resetForRefresh();
        long elapsed = System.currentTimeMillis() - start;
        feedback(ctx, "Filled All tab with " + count + " test messages in " + elapsed + "ms. "
            + "Now try: settings/scale changes, creating a Search over All, adding a Filtered tab rule.");
        return 1;
    }

    private static int debugClear(CommandContext<FabricClientCommandSource> ctx) {
        ChatTab allTab = ChatManager.getTabs().stream().filter(t -> t.type() == TabType.ALL).findFirst().orElse(null);
        if (allTab == null) { feedback(ctx, "No All tab found."); return 0; }
        int before = allTab.messageCount();
        allTab.messages().removeIf(me -> me.entry().originalContent != null
            && me.entry().originalContent.mc().getString().startsWith("Test message #"));
        int removed = before - allTab.messageCount();
        ChatManager.resetForRefresh();
        feedback(ctx, "Removed " + removed + " test messages.");
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
