# MultiChat

A Fabric 1.21 client-side mod that replaces the vanilla chat HUD with a multi-tab panel system. Supports two independent chat panels (left/right), tab filtering, search, notes, message selection, quick actions, and a full mod API for integration.

---

## For Players

- **Left panel** always visible; **right panel** optional (enable in Settings)
- Tab types: `ALL` (all messages), `VANILLA` (vanilla HUD passthrough), `FILTERED` (user-defined rules), `NOTES` (personal note-taking), `INFO` (mod guide), `SEARCH` (session-only query tabs), `API` (created by other mods)
- Right-click any message → Copy / Search / Jump to All / Delete message
- `Ctrl+F` → open search overlay
- Right-click any tab → context menu (rename, search in tab, set context, etc.)
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
| `/multichat info show` | Show the Info tab on Panel 2 |
| `/multichat info hide` | Hide the Info tab from Panel 2 |
| `/multichat info panel show` | Enable Panel 2 |
| `/multichat info panel hide` | Disable Panel 2 |
| `/multichat execute <id>` | Fire a TAB_ACTION event (used by API click events) |
| `/multichat help` | Open help screen |

---

## For Mod Developers — API Integration

### What the API provides

- Register named tabs owned by your mod
- Send messages into those tabs programmatically (with or without pings)
- Add messages silently, clear messages, or replace the full message list
- Set/clear the active **context** (prefix applied to all outgoing player chat)
- Subscribe to events: message received, message added to tab, tab changed, message sent, panel focused, tab action
- Query panels, focused panel, context tab, tabs by panel, and MultiChat config

---

### Adding MultiChat as a dependency

MultiChat is a **client-side** mod. MultiChat itself bundles `mobilelize-fabric-lib` as a nested Jar-in-Jar. When referencing MultiChat as a local file in Gradle, Loom does **not** automatically extract that nested jar — you need to pull it out manually and add it alongside MultiChat.

---

#### Step 1 — Extract the nested library jar

MultiChat's jar is a zip file. Inside it, at `META-INF/jars/`, is the embedded `mobilelize-fabric-lib` jar.

1. Rename `multichat-1.0.0.jar` to `multichat-1.0.0.zip` (or open directly with 7-Zip / any zip tool)
2. Navigate to `META-INF/jars/`
3. Extract `mobilelize-fabric-lib-x.x.x.jar`
4. Rename the zip back to `.jar`

---

#### Step 2 — Set up your `libs/` folder

Create a `libs/` folder at the root of your project and place both jars inside:

```
libs/
  multichat-1.0.0.jar
  mobilelize-fabric-lib-x.x.x.jar
```

---

#### Option A — Compile-only (MultiChat installed separately by the player)

Use this when you want to code against the API but ship your mod independently. Players must have MultiChat installed themselves.

```gradle
dependencies {
    modCompileOnly fileTree(dir: 'libs', include: ['*.jar'])
}
```

Also place both jars in your `run/mods/` folder so they load during dev runs. They will **not** be bundled into your built jar.

> Use this for a soft dependency — check `FabricLoader.getInstance().isModLoaded("multichat")` at runtime before calling any API.

---

#### Option B — Bundled (MultiChat ships inside your mod)

Use this when you want to guarantee MultiChat is present without requiring players to install it separately. Loom embeds both jars in your built jar via JiJ so Fabric Loader auto-installs them.

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

### 7. Panel access

```java
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

### 8. Click events via TAB_ACTION

Generate a UUID, attach it to a click event in a message, and listen for it via `TAB_ACTION`:

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

### 9. Events

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

### 10. Tab visibility

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

### 11. API tab filter rules

API tabs support the same whitelist/blacklist filter rules as FILTERED tabs. Users can configure these via the in-game tab editor (Edit button in Tab List). Rules apply to **incoming routed messages** (i.e. messages that arrive via MultiChat's routing pipeline). Messages sent via `tab.sendMessage()` or `tab.addMessageSilent()` are not filtered — they always appear.

---

### 12. Soft dependency pattern

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

## API class reference

All classes under `net.mobilelize.multichat.api` unless noted.

| Class / Interface            | Package   | Purpose |
|-----------------------------|-----------|---------|
| `MultiChatApi`              | `.api`    | Static entry point — register, events, read-only access |
| `MultiChatMod`              | `.api`    | Per-mod handle — create/remove tabs, list tab IDs |
| `TabHandle`                 | `.api`    | Per-tab handle — send messages, manage content, set context |
| `MessageReceivedCallback`   | `.api`    | Event: incoming `LzText` message, before routing (cancellable) |
| `MessageAddedToTabCallback` | `.api`    | Event: `ChatTab.MessageEntry` added to any tab |
| `TabChangedCallback`        | `.api`    | Event: active tab switched on a panel |
| `MessageSentCallback`       | `.api`    | Event: player sends chat (cancellable) |
| `PanelFocusedCallback`      | `.api`    | Event: focused panel changed (Ctrl+Arrow or tab click) |
| `TabActionCallback`         | `.api`    | Event: `/multichat execute <id>` fired (click event handler) |

---

## Notes

- MultiChat is **client-side only**. None of the API crosses the network.
- API tabs are **session-only** for messages — messages are not persisted to disk. Tab configuration (name, visibility, color, filter rules, context prefix) is saved and restored across sessions.
- SEARCH tabs are fully session-only and cannot be created via the API.
- `ChatTab` fields are public by design for read access. Prefer `TabHandle` methods for mutation to ensure config is saved correctly.
- `addMessageSilent` and `setMessages` bypass pings and `MESSAGE_ADDED_TO_TAB`. Use them when populating a tab from your own stored data to avoid false notifications.
