# MultiChat

A Fabric 1.21 client-side mod that replaces the vanilla chat HUD with a multi-tab panel system. Supports two independent chat panels (left/right), tab filtering, search, notes, message selection, quick actions, and a full mod API for integration.

Licensed under [GPL-3.0](LICENSE.txt).

---

## For Players

- Unlimited panels — **left panel** always visible by default, **right panel** optional (enable in Settings), add more via `/multichat panels`
- Tab types: `ALL` (all messages), `VANILLA` (vanilla HUD passthrough), `FILTERED` (user-defined rules), `NOTES` (personal note-taking), `INFO` (mod guide), `SEARCH` (session-only query tabs), `API` (created by other mods)
- **Multi-Tab** — any filtered tab can have nested sub-tabs. Enable via Tab Edit → `Multi Tab`; click the tab in the bar to drill into its sub-tabs, click the red `X` (or right-click → `Collapse Sub-tabs`) to back out. A sub-tab only ever sees what its parent's own filter already let through — the auto-created `All` sub-tab catches everything unfiltered, other sub-tabs narrow further with their own rules.
- **Highlight Words** — Settings → Highlight Words. Matching a keyword reformats the whole message into a two-color scheme (matched word / rest of message). Hover a highlighted message to see what matched; click it to revert. Your Keywords and API Words (added by other mods) are independent lists with their own on/off toggle. Exceptions (Your Exceptions + API Exceptions, also independently toggleable) shield a word from triggering a match — e.g. exception `urban` stops keyword `ban` from firing on it — and protect against **both** keyword pools regardless of which exception list they're in; the split is just for organization, and API Exceptions is your way to silence an API-registered word you can't otherwise remove for good.
- **Chat Themes** — Settings → Appearance → Chat Theme. Built-in: Vanilla, Simple Dark, Bold, Pink, Mint, Lavender. Mods can register their own via the API.
- Right-click any message → Copy / Search / Jump to All / Delete message / **Delete from All** (removes it from every tab that has it, including sub-tabs)
- Draggable scrollbar (glows on hover), **Always Focused** panel setting (stay fully visible permanently), **Pop Out** a tab into its own floating panel (right-click → Pop Out / Close Popout)
- `Ctrl+F` → open search overlay
- Right-click any tab → context menu (rename, search in tab, set context, pop out, etc.)
- Tab list and settings accessible via ModMenu or in-game config button
- `Ctrl+Arrow` → cycle focused panel; `Arrow` → switch tabs on focused panel
- Text cursor changes shape based on what it's hovering (message, tab, scrollbar, etc.)

---

### Commands

| Command | Description |
|---------|-------------|
| `/multichat` | Open Tab List screen |
| `/multichat tab` | Open Tab List screen |
| `/multichat tab add [name]` | Add new filtered tab (auto-named if no name given) |
| `/multichat tab remove` | Remove the active tab on the focused panel |
| `/multichat tab edit <name>` | Open tab editor for the named tab |
| `/multichat panels [name]` | Open Panel List / edit a specific panel |
| `/multichat layout` | Open panel layout GUI (drag panels to reposition) |
| `/multichat settings` | Open settings screen |
| `/multichat quickactions` | Open quick actions overlay |
| `/multichat quickactions edit` | Open quick actions editor |
| `/multichat context [tab]` | Set or clear the context tab (prefix applied to outgoing chat) |
| `/multichat note` | Open note editor for the active note tab |
| `/multichat note add <text>` | Append a line to the active note tab |
| `/multichat note open <name>` | Open note editor for a specific note tab |
| `/multichat highlight` | Open settings screen |
| `/multichat highlight add <word>` | Add a keyword to Your Keywords |
| `/multichat highlight remove <word>` | Remove a keyword from Your Keywords |
| `/multichat highlight exception add <word>` | Add a word to Your Exceptions |
| `/multichat highlight exception remove <word>` | Remove a word from Your Exceptions |
| `/multichat highlight api exception add <word>` | Add a word to API Exceptions |
| `/multichat highlight api exception remove <word>` | Remove a word from API Exceptions |
| `/multichat info show` | Show the Info tab on Panel 2 |
| `/multichat info hide` | Hide the Info tab from Panel 2 |
| `/multichat info panel show` | Enable Panel 2 |
| `/multichat info panel hide` | Disable Panel 2 |
| `/multichat execute <id>` | Fire a TAB_ACTION event (used by API click events) |
| `/multichat help` | Open help screen |

---

## For Mod Developers — API Integration

### What the API provides

- Register named tabs owned by your mod — and know if it's your very first time ever registering, so you only seed defaults once
- Send messages into those tabs programmatically (with or without pings)
- Add messages silently, clear messages, or replace the full message list
- Fully configure a tab in code — whitelist/blacklist filter rules, ping conditions, timestamps, collapse, difference mode, message background color — no player setup required
- Give a tab **nested sub-tabs** (Multi-Tab), each with their own filter rules layered under the parent
- **Create and configure your own panels** — position, size, scale, opacity, always-focused, tabs-at-top — not just read existing ones
- Set/clear the active **context** (prefix applied to all outgoing player chat)
- One-call click-to-run-a-callback helper — no need to hand-build the `/multichat execute` command yourself
- Delete a message from every tab it appears in (moderation use cases)
- Register keyword-highlight rules and exception words in code — same effect as the player's own Highlight Words lists, mutable/toggleable by the player independently of your defaults
- Register your own chat theme (color palette)
- Subscribe to events: message received, message added to tab, tab changed, message sent, panel focused, tab action
- Query panels, focused panel, context tab, tabs by panel, and MultiChat config

---

### Adding MultiChat as a dependency

MultiChat is a **client-side** mod. `mobilelize-fabric-lib` is no longer a separate nested jar — its code now lives directly inside MultiChat, so there is nothing to extract. Just grab the MultiChat jar.

> **Cloth Config is a hard dependency.** MultiChat's own `fabric.mod.json` requires `cloth-config` (`"depends": {"cloth-config": "*"}`) and does **not** bundle it (`modApi`, not `include`/JiJ — see MultiChat's own `build.gradle`). This means Cloth Config Fabric API must be present wherever MultiChat runs, including **your own dev environment** — Fabric Loader will refuse to load MultiChat (and therefore your mod) without it. Either add it as your own dependency the same way (`modApi "me.shedaniel.cloth:cloth-config-fabric:<version>"`, currently `21.11.153`), or just drop its jar in your `run/mods/` folder alongside MultiChat's. ModMenu is *not* a hard dependency — MultiChat's ModMenu entrypoint is only invoked if ModMenu happens to be present, so it's fine to skip.

---

#### Set up your `libs/` folder

Create a `libs/` folder at the root of your project and place the jar inside:

```
libs/
  multichat-1.0.0.jar
  cloth-config-fabric-21.11.153.jar
```

---

#### Option A — Compile-only (MultiChat installed separately by the player)

Use this when you want to code against the API but ship your mod independently. Players must have MultiChat installed themselves.

```gradle
dependencies {
    modCompileOnly fileTree(dir: 'libs', include: ['*.jar'])
}
```

Also place the jar in your `run/mods/` folder so it loads during dev runs. It will **not** be bundled into your built jar.

> Use this for a soft dependency — check `FabricLoader.getInstance().isModLoaded("multichat")` at runtime before calling any API.

---

#### Option B — Bundled (MultiChat ships inside your mod)

Use this when you want to guarantee MultiChat is present without requiring players to install it separately. Loom embeds the jar in your built jar via JiJ so Fabric Loader auto-installs it.

```gradle
dependencies {
    modImplementation fileTree(dir: 'libs', include: ['*.jar'])
    include fileTree(dir: 'libs', include: ['*.jar'])
}
```

> **Note:** If a player already has MultiChat installed separately, Fabric Loader uses whichever version is newer and ignores the bundled one. No conflict.

---

### Declaring the dependency in `fabric.mod.json`

Declare MultiChat as a dependency so Fabric Loader knows about it:

```json
"depends": {
    "multichat": "*"
}
```

Or for a soft/optional dependency:

```json
"suggests": {
    "multichat": "*"
}
```

---

## API Usage

All API entry points are in `net.mobilelize.multichat.api`.

### 1. Register your mod and create a tab

Tabs can be registered at any time — on startup, on server join, or dynamically during play. You are not limited to one tab per mod.

```java
MultiChatMod api = MultiChatApi.register("mymod");

// All panels (default)
TabHandle tab = api.registerTab("alerts", "Alerts");

// Specific panels by ID (0 = left, 1 = right)
TabHandle tab = api.registerTab("alerts", "Alerts", 0);        // left only
TabHandle tab = api.registerTab("alerts", "Alerts", 0, 1);     // both panels

// Set color or visibility after registration (fluent)
tab.setColor(0xFF5500);
tab.setVisibleOnPanels(1);       // right panel only
tab.setVisibleOnPanel(2, true);  // add panel 2 without changing others
```

The full internal ID is `modid:name` (e.g. `mymod:alerts`). If you call `registerTab` with the same name and the tab still exists, the existing handle is returned. If the tab was removed (by the player or your code), `registerTab` creates a fresh one — so it is safe to call on every server join or reconnect.

User customizations (visibility, color, filter rules, context prefix) made via the in-game tab editor are automatically restored from config on the next `registerTab` call.

**First-ever registration.** `register()`/`registerTab()` are always safe to call again on every launch — but if the player deliberately deleted a tab you created, calling `registerTab` for it again would recreate it. Check `isFirstEverRegistration` (persisted across restarts) to decide whether to seed default tabs at all:

```java
MultiChatMod api = MultiChatApi.register("mymod");
if (MultiChatApi.isFirstEverRegistration("mymod")) {
    api.registerTab("alerts", "Alerts");   // only ever created once
}
```

---

### 2. Send messages

```java
tab.sendMessage("Hello from mymod!");
tab.sendMessage(Text.literal("Hello ").append(Text.literal("world").formatted(Formatting.GREEN)));

// Also route through the vanilla pipeline (adds to All + Vanilla + Filtered tabs)
tab.sendMessage(message, true);
```

Messages are prepended to the top of the tab. The tab's message cap (0 = unlimited) is respected. An unread badge increments if the tab is not currently active.

---

### 3. Message access and control (silent)

These methods bypass pings and the `MESSAGE_ADDED_TO_TAB` event — use them when you want to populate or manage a tab's content without notifying the player.

```java
// Read stored messages
List<LzText> messages = tab.getMessages();                    // content only
List<ChatTab.MessageEntry> entries = tab.getMessageEntries(); // full entry data

// Add a message without ping or event
tab.addMessageSilent("System notice");
tab.addMessageSilent(LzText.format("&aGreen text"));

// Replace all messages at once (oldest → newest order, no cap enforced)
tab.setMessages(List.of(msg1, msg2, msg3));

// Clear all messages from tab and all panels
tab.clearMessages();

// Unread badge
int unread = tab.getUnreadCount();
tab.clearUnread();
```

This is enough control to treat a ROUTED tab like a CUSTOM_CONTENT tab — you can manage the full stored message list yourself without going through the routing pipeline.

---

### 4. Custom content mode

API tabs can switch between showing routed messages and a fully custom line list:

```java
// Push a custom list of lines (switches to custom content mode automatically)
tab.setContent(List.of(LzText.literal("Line 1"), LzText.literal("Line 2")));

// Reset scroll to top when setting content
tab.setContent(lines, true);

// Switch mode without changing content
tab.useCustomContent();    // show the custom list
tab.useRoutedMessages();   // show routed message history

// Check current mode
boolean custom = tab.isCustomContent();
```

---

### 5. Context (prefix applied to outgoing chat)

Set a tab as the active context so every message the player types gets a prefix prepended:

```java
tab.setContextPrefix("msg Fred");       // prefix string
tab.setContextCommandMode(true);        // true = sent as /command, false = as chat text
tab.setAsContext();                      // activate this tab's context
tab.clearContext();                      // deactivate (only if this tab is active)
MultiChatApi.clearContext();             // global clear regardless of active tab
```

The context indicator (`▶ Tab: Alerts [msg Fred]`) shown above the chat bar can be toggled by the user in Settings → General → Show Context Display.

---

### 6. Tab management and lifecycle

```java
// Get a handle you registered earlier
TabHandle handle = api.getTab("alerts");

// Get all full IDs registered by your mod ("mymod:alerts", "mymod:chat", etc.)
List<String> ids = api.getTabIds();

// Remove a tab entirely (fires onRemoved callback)
api.unregisterTab("alerts");

// Check if a handle is still valid (tab exists and client is ready)
boolean alive = tab.isReady();

// React when a tab is removed for any reason
tab.onRemoved(() -> {
    // tab was deleted — clean up references, cancel timers, etc.
});

// Allow the player to delete this tab via the tab list or /multichat tab remove
// Default is false — API tabs are not player-deletable unless you opt in
tab.setPlayerDeletable(true);

// Read-only access to all tabs in the system
List<ChatTab> all = MultiChatApi.getTabs();

// Find a tab by full ID
ChatTab tab = MultiChatApi.getTab("mymod:alerts");
```

Handles become invalid when their tab is removed. After removal, `isReady()` returns `false` and send/mutate methods are no-ops or return `false`. To re-create a tab with the same name, call `registerTab` again — it detects the stale handle and creates a fresh tab.

---

### 7. Filter rules

A tab can be fully self-configured in code — same `FilterRuleSet`/`FilterRule` mechanics as the player-facing Whitelist/Blacklist screens, no player setup required:

```java
tab.addWhitelistRule("reported");                       // shorthand: CONTAINS, any indicator
tab.addWhitelistRule(FilterType.REGEX, "^\\[S\\]");      // shorthand: any indicator (the common case)
tab.addWhitelistRule(FilterType.REGEX, "^\\[S\\]", IndicatorFilter.SYSTEM); // full form when it matters
tab.addBlacklistRule("spam keyword");
tab.setWhitelistMode(WhitelistMode.ANY);   // ANY: match any rule. ALL: must match every rule.
tab.setBlacklistMode(BlacklistMode.ANY);   // ANY: exclude if any rule matches. ALL: exclude only if all match.

List<FilterRule> wl = tab.getWhitelistRules();
List<FilterRule> bl = tab.getBlacklistRules();
tab.clearWhitelistRules();
tab.clearBlacklistRules();

// Rescan the All tab's full history against the current rules and rebuild — otherwise new rules
// only apply to messages that arrive from now on.
tab.refilter();
```

---

### 8. Style rules

Separate from filter rules (section 7) — these check how part of a message actually **renders** (bold, italic, underline, strikethrough, color, and whether it has a click/hover event), not its text. A message must satisfy both filter rules and style rules (if either is set) to be routed there. Same whitelist/blacklist shape as filter rules.

```java
// Require the first word to be italic AND colored gray — e.g. verifying a "[S]" sender prefix
// actually renders styled, so plain text alone can't spoof it.
tab.addWhitelistStyleCondition(StyleTarget.WORD, "", WordLocation.START,
    List.of(StyleFlag.ITALIC), List.of(StyleColor.GRAY), List.of());

// Pin to an exact word position instead of first/last — addWhitelistWordAt is shorthand for
// StyleTarget.WORD + WordLocation.INDEX, since picking an index already implies both
tab.addWhitelistWordAt(2, "Jack", List.of(StyleFlag.BOLD), List.of(), List.of());

// START_OF_MESSAGE / END_OF_MESSAGE check the first/last word's style, Word field ignored
tab.addBlacklistStyleCondition(StyleTarget.END_OF_MESSAGE, "", WordLocation.ANYWHERE,
    List.of(StyleFlag.OBFUSCATED), List.of(), List.of());

// hexColors is for colors outside the 16 named StyleColor values — a character matches if its
// color is in colors OR hexColors, so a multi-colored word can still match as a whole
tab.addWhitelistStyleCondition(StyleTarget.WORD, "alert", WordLocation.ANYWHERE,
    List.of(), List.of(), List.of(0xFF8800));

// HAS_CLICK: does word 2 have a click event at all? Only part of a tokenized word needs it — e.g.
// "<Steve>" where only "Steve" carries the ClickEvent, not the brackets. See flags note below.
tab.addWhitelistWordAt(2, "", List.of(StyleFlag.HAS_CLICK), List.of(), List.of());

tab.setStyleWhitelistMode(WhitelistMode.ANY);
tab.setStyleBlacklistMode(BlacklistMode.ANY);

List<StyleCondition> swl = tab.getWhitelistStyleConditions();
List<StyleCondition> sbl = tab.getBlacklistStyleConditions();
tab.clearWhitelistStyleConditions();
tab.clearBlacklistStyleConditions();
```

`word` left blank skips content matching and just checks formatting at that location — for `WordLocation.ANYWHERE` this becomes an **existence** check instead (does this style appear anywhere in the message, at least one character), rather than pinning to one word.

`addWhitelistWordAt`/`addBlacklistWordAt` are shorthand for the `StyleTarget.WORD` + `WordLocation.INDEX` combination — pass just the index (1 = first word, 2 = second, etc.) and a word (blank = don't care what it says) instead of specifying target and word location yourself, since choosing an index already implies both.

`flags` (empty allowed) requires every listed flag at once. Formatting flags (`BOLD`/`ITALIC`/`UNDERLINE`/`STRIKETHROUGH`/`OBFUSCATED`) must hold **uniformly** across every character in the checked range/word — that's what makes the spoof-guard example above work (the whole prefix must genuinely be italic). Interaction flags (`HAS_CLICK`, `HAS_HOVER`, `CLICK_RUN_COMMAND`, `CLICK_SUGGEST_COMMAND`, `CLICK_OPEN_URL`, `CLICK_COPY_TO_CLIPBOARD`, `HOVER_SHOW_TEXT`) instead only need **at least one** character in range to satisfy them — a clickable name is usually a substring of the tokenized word (brackets/punctuation around it typically aren't clickable), so requiring uniformity there would almost never match. `colors`/`hexColors` (both empty allowed, but at least one of flags/colors/hexColors must be set) require every character in range to be one of the listed colors, not necessarily the same one throughout.

---

### 9. Multi-Tab (nested sub-tabs)

Turn a tab into a container for its own filtered sub-tabs — same layered-filter mechanic as the player-facing `Multi Tab` toggle. A sub-tab only ever sees what its parent's own filter already let through.

```java
tab.setMultiTab(true);               // auto-creates the protected "All" sub-tab (index 0)
boolean multi = tab.isMultiTab();

// addSubTab turns multi-tab on automatically if it wasn't already, and returns a full TabHandle —
// every other method here (filter rules, pings, messages, sendMessage...) works on it identically.
TabHandle reports = api.registerTab("reports", "Reports");
TabHandle regionA = reports.addSubTab("Region A");
regionA.addWhitelistRule("region: A");

TabHandle all = reports.getAllSubTab();       // the always-present catch-all sub
List<TabHandle> subs = reports.getSubTabs();  // includes the "All" sub at index 0
reports.removeSubTab("Region A");             // the protected "All" sub can't be removed this way
```

---

### 10. More tab settings

Everything the player-facing Tab Edit screen can set is available on `TabHandle`:

```java
tab.setShowTimestamps(true);
tab.setMsgBgColor(0x1A1A1A);            // 0 = inherit panel/theme default
tab.setCollapseRepeating(true);
tab.setCollapseMatchFormatting(true);   // only matters if collapseRepeating is also on

// Difference mode — layered pipeline, lower-index layers claim messages first
tab.setDifferenceMode(true);
tab.setDifferenceLayerIndex(2);
tab.setDifferenceException(true);   // receives messages normally, never blocks the pipeline
tab.setDifferenceInclusion(true);   // blocks the pipeline even when not the active tab

// Every setter above has a matching getter, e.g.:
boolean ts = tab.getShowTimestamps();
```

---

### 11. Panels

Panels are no longer read-only — create and fully configure your own via `PanelHandle`:

```java
PanelHandle panel = MultiChatApi.createPanel("My Panel");   // blank, enabled, centered — same as api.createPanel(name)

panel.setPosition(0.5f, 0.5f);        // fraction of screen width/height, CUSTOM anchor
panel.setWidth(300);
panel.setFocusedHeightLines(10);
panel.setScale(1.0f);
panel.setBgColor(0x1A1A1A);
panel.setBgOpacity(0.8);
panel.setChatOpacity(1.0);
panel.setAlwaysFocused(true);         // stays fully visible permanently
panel.setTabsAtTop(false);
panel.setShowTabs(true);

panel.addTab(tab);                    // attach a TabHandle to this panel
panel.removeTab(tab);
panel.remove();                       // delete the panel entirely

// Read-only access to every panel in the system
ChatPanel left  = MultiChatApi.getLeftPanel();
ChatPanel right = MultiChatApi.getRightPanel();
ChatPanel byId  = MultiChatApi.getPanel(0);                   // 0 = left, 1 = right
List<ChatPanel> all = MultiChatApi.getPanels();

// Currently focused panel (last Ctrl+Arrow or tab click)
ChatPanel focused = MultiChatApi.getFocusedPanel();

// Active context tab
ChatTab ctx = MultiChatApi.getContextTab();

// All tabs visible on a given panel
List<ChatTab> onLeft = MultiChatApi.getTabsOnPanel(0);

// Shortcut to the ALL tab
ChatTab allTab = MultiChatApi.getAllTab();

// Current MultiChat config (read-only)
MultiChatConfig config = MultiChatApi.getConfig();
```

---

### 12. Click events

One call generates the action id, registers the callback, and returns a ready-to-use click style:

```java
LzText shown = original.withStyle(tab.newAction(() -> {
    // handle click
}));

tab.sendMessage(shown);
```

Or build it by hand if you need the raw command string (e.g. to attach it yourself with `.withClickRunCommand(...)`, or via vanilla `ClickEvent`):

```java
String actionId = tab.newActionId();

Text clickable = Text.literal("[Click me]")
    .styled(s -> s.withClickEvent(
        new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/multichat execute " + actionId)));

tab.sendMessage(LzText.wrapMc(clickable));

MultiChatApi.TAB_ACTION.register(id -> {
    if (id.equals(actionId)) {
        // handle click
    }
});
```

---

### 13. Delete a message everywhere

Deletes a message (by its `LzChatEntry` identity, from `tab.getMessageEntries()`) from every tab that has it, including multi-tab sub-tabs — same as the player-facing "Delete from All" context menu item:

```java
ChatTab.MessageEntry entry = tab.getMessageEntries().get(0);
MultiChatApi.deleteMessageEverywhere(entry.entry());
```

---

### 14. Chat themes

Register a color palette — shows up in the theme picker (Settings → Appearance) alongside the built-ins:

```java
ChatTheme theme = new ChatTheme("mymod_theme", "My Theme");
theme.tabBgActive = 0xCC2A2A34;
theme.tabTextActive = 0xFFFFFFFF;
// ... set every field on ChatTheme (see the class for the full slot list)

MultiChatApi.registerTheme(theme);
MultiChatApi.setActiveTheme("mymod_theme");
ChatTheme active = MultiChatApi.getActiveTheme();
```

---

### 15. Events

All events use the standard Fabric Event API — call `.register()` on the event field.

#### `MultiChatApi.MESSAGE_RECEIVED`
Fires before MultiChat routes an incoming chat message. Param is `LzText` (fabriclib wrapper). Return `true` to cancel routing (message is suppressed from all tabs).

```java
MultiChatApi.MESSAGE_RECEIVED.register(message -> {
    if (message.mc().getString().contains("secret")) return true; // suppress
    return false;
});
```

#### `MultiChatApi.MESSAGE_ADDED_TO_TAB`
Fires after a message lands in any tab (including API and FILTERED tabs). Not fired by `addMessageSilent` / `setMessages`. Second param is `ChatTab.MessageEntry` (holds rendered form, content `LzText`, and raw `LzChatEntry`).

```java
MultiChatApi.MESSAGE_ADDED_TO_TAB.register((tab, entry) -> {
    if ("mymod:alerts".equals(tab.apiId())) {
        String text = entry.content().mc().getString();
    }
});
```

#### `MultiChatApi.TAB_CHANGED`
Fires when the user switches the active tab on either panel.

```java
MultiChatApi.TAB_CHANGED.register((panel, newTab) -> {
    System.out.println("Switched to: " + newTab.name());
});
```

#### `MultiChatApi.MESSAGE_SENT`
Fires when the player sends a chat message (non-command). Return `true` to cancel sending.

```java
MultiChatApi.MESSAGE_SENT.register(message -> {
    if (message.startsWith("!")) {
        handleCommand(message.substring(1));
        return true; // cancel — don't send to server
    }
    return false;
});
```

#### `MultiChatApi.PANEL_FOCUSED`
Fires when the focused panel changes (Ctrl+Arrow or tab click that switches focus). `previous` is `null` if no panel was focused before.

```java
MultiChatApi.PANEL_FOCUSED.register((panel, previous) -> {
    System.out.println("Focused panel: " + panel.id());
});
```

#### `MultiChatApi.TAB_ACTION`
Fires when `/multichat execute <id>` is run. Use with `tab.newActionId()` to handle in-chat click events.

```java
MultiChatApi.TAB_ACTION.register(actionId -> {
    if (actionId.equals(myButtonId)) {
        // button was clicked
    }
});
```

---

### 16. Tab visibility

Tabs track which panels they appear on as a set of panel IDs. Panel IDs are integers assigned per panel (0 = left by default, 1 = right, and so on for any additional panels).

```java
tab.setVisibleOnPanels(0, 1);     // show on both default panels
tab.setVisibleOnPanels(0);        // left only
tab.setVisibleOnPanels();         // hide from all panels (messages still received)
tab.setVisibleOnPanel(2, true);   // add to panel 2 without changing others
tab.setVisibleOnPanel(0, false);  // remove from panel 0 only
Set<Integer> ids = tab.getVisiblePanels();
```

---

### 17. API tab filter rules — how matching works

Whether set via `tab.addWhitelistRule(...)` (section 7) or by the player through the in-game tab editor, rules apply to **incoming routed messages** (i.e. messages that arrive via MultiChat's routing pipeline). Messages sent via `tab.sendMessage()` or `tab.addMessageSilent()` are not filtered — they always appear.

---

### 18. Soft dependency pattern

If MultiChat is optional in your mod:

```java
// In your ClientModInitializer
if (FabricLoader.getInstance().isModLoaded("multichat")) {
    MultiChatIntegration.init();
}

// In a separate class (so it only classloads if multichat is present)
public class MultiChatIntegration {
    public static void init() {
        MultiChatMod api = MultiChatApi.register("mymod");
        TabHandle tab = api.registerTab("alerts", "Alerts");
        // ...
    }
}
```

---

### 19. Highlight Words (keyword highlighting)

Register keywords and exception words in code — same engine the player's own Highlight Words lists (Settings → Highlight Words) run on. A keyword match reformats the whole message into a two-color scheme; an exception word overlapping a match blocks it from firing, checked against **every** keyword pool (yours, other mods', the player's) regardless of which exception list it's registered through.

```java
// Keywords — muted in bulk by the player's "Enable API Words" toggle
MultiChatApi.addHighlightRule("giveaway", "mymod");
MultiChatApi.removeHighlightRule("giveaway", "mymod");

// Exceptions — muted in bulk by the player's "Enable API Exceptions" toggle
MultiChatApi.addHighlightException("giveaway tree", "mymod");
MultiChatApi.removeHighlightException("giveaway tree", "mymod");

// Seed your defaults exactly once — persisted across restarts, so re-adding
// them every launch would silently undo any word the player removed.
if (MultiChatApi.isFirstHighlightRegistration("mymod")) {
    MultiChatApi.addHighlightRule("giveaway", "mymod");
}
```

Both calls are idempotent (safe on hot reload / repeated init) and both are player-removable only through the read-only "API Words" / "API-Registered Exceptions" lists in Settings — the player can't edit those lists directly, but can counter an unwanted keyword by adding their own exception word for it.

---

## API class reference

All classes under `net.mobilelize.multichat.api` unless noted.

| Class / Interface            | Package   | Purpose |
|-----------------------------|-----------|---------|
| `MultiChatApi`              | `.api`    | Static entry point — register, events, panels, themes, read-only access |
| `MultiChatMod`              | `.api`    | Per-mod handle — create/remove tabs, create panels, list tab IDs |
| `TabHandle`                 | `.api`    | Per-tab handle — messages, content, filter rules, multi-tab, ping/timestamp/collapse/diff settings, context, click actions |
| `PanelHandle`                | `.api`    | Per-panel handle — position, size, scale, opacity, always-focused, attach/detach tabs, remove |
| `MessageReceivedCallback`   | `.api`    | Event: incoming `LzText` message, before routing (cancellable) |
| `MessageAddedToTabCallback` | `.api`    | Event: `ChatTab.MessageEntry` added to any tab |
| `TabChangedCallback`        | `.api`    | Event: active tab switched on a panel |
| `MessageSentCallback`       | `.api`    | Event: player sends chat (cancellable) |
| `PanelFocusedCallback`      | `.api`    | Event: focused panel changed (Ctrl+Arrow or tab click) |
| `TabActionCallback`         | `.api`    | Event: `/multichat execute <id>` fired (click event handler) |

---

## Notes

- MultiChat is **client-side only**. None of the API crosses the network.
- API tabs are **session-only** for messages — messages are not persisted to disk. Tab configuration (name, visibility, color, filter rules, context prefix, multi-tab/sub-tabs) is saved and restored across sessions.
- SEARCH tabs are fully session-only and cannot be created via the API.
- `ChatTab` fields are public by design for read access. Prefer `TabHandle` methods for mutation to ensure config is saved correctly. Same goes for `ChatPanel` and `PanelHandle`.
- `addMessageSilent` and `setMessages` bypass pings and `MESSAGE_ADDED_TO_TAB`. Use them when populating a tab from your own stored data to avoid false notifications.
- Sub-tabs (Multi-Tab) are never in `MultiChatApi.getTabs()` — they're only reachable via `TabHandle.getSubTabs()`/`getAllSubTab()`, since they're logically owned by their parent, not the global tab list.
- Style rules (section 8) are a separate whitelist/blacklist from filter rules (section 7) — a message must satisfy both, if either has rules set. Style conditions live in `net.mobilelize.multichat.client.filter` (`StyleCondition`, `StyleTarget`, `WordLocation`, `StyleFlag`, `StyleColor`), not the `.api` package.
