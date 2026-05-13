# MultiChat

A Fabric 1.21 client-side mod that replaces the vanilla chat HUD with a multi-tab panel system. Supports two independent chat panels (left/right), tab filtering, search, message selection, and a full mod API for integration.

---

## For Players

- **Left panel** always visible; **right panel** optional (enable in Settings)
- Tabs: `ALL` (all messages), `VANILLA` (vanilla HUD passthrough), `FILTERED` (user-defined rules), `SEARCH` (session-only query tabs), `API` (created by other mods)
- Right-click any message → Copy / Search / Jump to All / Delete message
- `Ctrl+F` → open search overlay
- Right-click any tab → context menu (rename, search in tab, set context, etc.)
- Tab list and settings accessible via ModMenu or in-game config button

---

## For Mod Developers — API Integration

### What the API provides

- Register named tabs owned by your mod
- Send messages into those tabs programmatically
- Set/clear the active **context** (prefix applied to all outgoing player chat)
- Subscribe to events: message received, message added to tab, tab changed, message sent
- Query all registered tabs and read MultiChat config

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

Call this during your `ClientModInitializer.onInitializeClient()`:

```java
MultiChatMod api = MultiChatApi.register("mymod");

// Minimal — defaults: visibility=BOTH, color=gray
TabHandle tab = api.registerTab("alerts", "Alerts");

// With visibility
TabHandle tab = api.registerTab("alerts", "Alerts", TabVisibility.LEFT);

// With color
TabHandle tab = api.registerTab("alerts", "Alerts", 0xFF5500);

// Full control
TabHandle tab = api.registerTab("alerts", "Alerts", TabVisibility.BOTH, 0xFF5500);
```

The full internal ID is `modid:name` (e.g. `mymod:alerts`). Calling `registerTab` with the same name a second time returns the existing handle — safe to call on reconnect.

User customizations (visibility, color, filter rules, context prefix) made via the in-game tab editor are automatically restored from config on the next `registerTab` call.

---

### 2. Send messages

```java
tab.sendMessage("Hello from mymod!");
tab.sendMessage(Text.literal("Hello ").append(Text.literal("world").formatted(Formatting.GREEN)));
```

Messages are prepended to the top of the tab. The tab's message cap (0 = unlimited) is respected. An unread badge increments if the tab is not currently active.

---

### 3. Context (prefix applied to outgoing chat)

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

### 4. Tab management

```java
// Get a handle you registered earlier
TabHandle handle = api.getTab("alerts");

// Get all full IDs registered by your mod ("mymod:alerts", "mymod:chat", etc.)
List<String> ids = api.getTabIds();

// Remove a tab entirely
api.unregisterTab("alerts");

// Read-only access to all tabs in the system
List<ChatTab> all = MultiChatApi.getTabs();

// Find a tab by full ID
ChatTab tab = MultiChatApi.getTab("mymod:alerts");

// Active panel state
ChatPanel left  = MultiChatApi.getLeftPanel();
ChatPanel right = MultiChatApi.getRightPanel();
```

---

### 5. Events

All events use the standard Fabric Event API — call `.register()` on the event field.

#### `MultiChatApi.MESSAGE_RECEIVED`
Fires before MultiChat routes an incoming chat message. Return `true` to cancel routing (message is suppressed from all tabs).

```java
MultiChatApi.MESSAGE_RECEIVED.register(message -> {
    if (message.getString().contains("secret")) return true; // suppress
    return false;
});
```

#### `MultiChatApi.MESSAGE_ADDED_TO_TAB`
Fires after a message lands in any tab (including API and FILTERED tabs).

```java
MultiChatApi.MESSAGE_ADDED_TO_TAB.register((tab, line) -> {
    if (tab.apiId != null && tab.apiId.equals("mymod:alerts")) {
        // react to messages added to your tab
    }
});
```

#### `MultiChatApi.TAB_CHANGED`
Fires when the user switches the active tab on either panel.

```java
MultiChatApi.TAB_CHANGED.register((panel, newTab) -> {
    System.out.println("Switched to: " + newTab.name);
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

---

### 6. Tab visibility options

`TabVisibility` controls which panel(s) a tab appears in:

| Value    | Description                        |
|----------|------------------------------------|
| `BOTH`   | Shown in both left and right panels |
| `LEFT`   | Left panel only                    |
| `RIGHT`  | Right panel only                   |
| `NONE`   | Hidden from all panels (messages still received) |

---

### 7. API tab filter rules

API tabs support the same whitelist/blacklist filter rules as FILTERED tabs. Users can configure these via the in-game tab editor (Edit button in Tab List). Rules apply to **incoming routed messages** (i.e. messages that arrive via MultiChat's routing pipeline). Messages sent via `tab.sendMessage()` are not filtered — they always appear.

---

### 8. Soft dependency pattern

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

| Class / Interface          | Purpose |
|---------------------------|---------|
| `MultiChatApi`            | Static entry point — register, events, read-only access |
| `MultiChatMod`            | Per-mod handle — create/remove tabs, list tab IDs |
| `TabHandle`               | Per-tab handle — send messages, set context, set prefix |
| `MessageReceivedCallback` | Event: incoming message (cancellable) |
| `MessageAddedToTabCallback` | Event: message added to any tab |
| `TabChangedCallback`      | Event: active tab switched on a panel |
| `MessageSentCallback`     | Event: player sends chat (cancellable) |

---

## Notes

- MultiChat is **client-side only**. None of the API crosses the network.
- API tabs are **session-only** for messages — messages are not persisted to disk. Tab configuration (name, visibility, color, filter rules, context prefix) is saved and restored across sessions.
- SEARCH tabs are fully session-only and cannot be created via the API.
- `ChatTab` fields are public by design for read access. Prefer the `TabHandle` methods for mutation to ensure config is saved correctly.
