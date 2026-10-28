package net.mobilelize.multichat.integration.chatheads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.ObjectContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.multichat.client.MultichatClient;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Compatibility shim for dzwdz/chat_heads. Only this class touches chat_heads classes —
 * MultiChat internals must never import dzwdz.chat_heads.* directly.
 * <p>
 * Built against chat_heads 1.3.x (github.com/dzwdz/chat_heads, tag 1.3.2-26.3). Both render
 * positions put the head into the message {@code Text} as a vanilla {@code ObjectContents}
 * player sprite, so MultiChat's normal text drawing paints it in every tab:
 * <ul>
 *   <li>BEFORE_NAME: ChatListenerMixin rewrites the {@code addMessage} argument, which
 *       ChatHudMixin.captureRaw then captures as-is.</li>
 *   <li>BEFORE_LINE: ChatComponentMixin2.prependChatHead only rewrites the text of the
 *       {@code GuiMessage} vanilla stores — later than captureRaw — so
 *       {@link #withBeforeLineHead} applies the same step to MultiChat's copy.</li>
 * </ul>
 * BEFORE_LINE alignment is chat_heads' {@code getChatOffset(line)}: with "offset non-player text"
 * on, every line that doesn't start with a head is shifted right by {@code headWidth()}
 * ({@link #lineOffset}).
 * <p>
 * Glyph padding and 3D-ness ({@code PaddedChatGlyph}, {@code PlayerGlyphProviderInstanceMixin})
 * only apply while {@code ChatHeads.customHeadRendering} is true. chat_heads sets it for vanilla
 * chat and for GuiRenderer.prepare (where queued text is actually drawn); MultiChat brackets its
 * own width measurements the same way ({@link #beginHeadRendering}) so they match what's drawn.
 * <p>
 * chat_heads 1.2.x (before 1.3.0) drew BEFORE_LINE heads beside the line instead of in the text;
 * that path is kept ({@link #headsInText} false) so an older install still works.
 */
public final class ChatHeadsSupport {

    private static volatile boolean present = detect();

    // Callers (ChatRenderer's draw loop, ChatSelection's click/highlight math, resolveStyleAt) each
    // bracket their own width-sensitive work independently and can nest (e.g. selection math running
    // inside a render pass). Depth-count so only the outermost begin/end pair flips chat_heads' flag.
    // Single-threaded render/tick, no synchronization needed.
    private static int depth = 0;

    // chat_heads 1.3.0 (commit a0ae814) made BEFORE_LINE "work like BEFORE_NAME": the head is put
    // into the text instead of drawn beside the line. 1.2.9 doesn't, 1.3.0 does. Read from the mod
    // version so no chat_heads class has to be loaded to find out.
    private static final boolean headsInText = present && detectHeadsInText();

    private ChatHeadsSupport() {}

    private static boolean detect() {
        try {
            boolean loaded = FabricLoader.getInstance().isModLoaded("chat_heads");
            if (loaded) MultichatClient.LOGGER.info("[MultiChat] Chat Heads detected, compatibility enabled");
            return loaded;
        } catch (Throwable t) {
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads present but could not be initialized", t);
            return false;
        }
    }

    private static boolean detectHeadsInText() {
        try {
            var mod = FabricLoader.getInstance().getModContainer("chat_heads");
            if (mod.isEmpty()) return false;
            return mod.get().getMetadata().getVersion()
                .compareTo(net.fabricmc.loader.api.SemanticVersion.parse("1.3.0")) >= 0;
        } catch (Throwable t) {
            MultichatClient.LOGGER.warn("[MultiChat] Could not read the Chat Heads version", t);
            return false;
        }
    }

    /** Call once from {@code onInitializeClient} so the detect log line lands at startup,
     *  not whenever the first chat line happens to render. */
    public static void init() {}

    public static boolean isPresent() { return present; }

    // Everything that decides wrap-time widths, sampled once a tick by pollLayoutChanged(). chat_heads'
    // config screen has no save callback we can hook, so a change is only noticed by comparing. Render
    // offsets read config live; only widths baked into already-wrapped lines need a rebuild.
    private static String lastLayoutKey = null;

    /** Call once a tick (see MultichatClient). True the moment render position, the "offset non-player
     *  text" state or the head width (right padding / 3D-ness) changed since last tick — callers
     *  rebuild every panel's wrapped lines (ChatManager.resetForRefresh()). Never fires on the first
     *  call, so mod startup doesn't trigger a spurious rebuild. */
    public static boolean pollLayoutChanged() {
        if (!present) return false;
        try {
            String key = dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() + "|" + offsetEnabled()
                + "|" + dzwdz.chat_heads.ChatHeads.headWidth();
            boolean changed = lastLayoutKey != null && !key.equals(lastLayoutKey);
            lastLayoutKey = key;
            return changed;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    // ── Capture (ChatHudMixin.captureRaw, HEAD of ChatComponent.addMessage) ────────────────────────────
    //
    // chat_heads' ChatListenerMixin sets ChatHeads.lastSenderData right before vanilla addMessage
    // runs and ChatComponentMixin2 resets it at addMessage's RETURN, so at HEAD it belongs to exactly
    // this message.

    /** Opaque handle to a chat_heads HeadData — lets code outside this package hold a reference
     *  without importing chat_heads types. {@link #NONE} means "never went through chat_heads". */
    public static final class HeadRef {
        public static final HeadRef NONE = new HeadRef(null);
        private final dzwdz.chat_heads.HeadData data;
        private HeadRef(dzwdz.chat_heads.HeadData data) { this.data = data; }
    }

    // Self-cleans as entries scroll out of every tab's history and become unreachable.
    private static final Map<LzChatEntry, HeadRef> headByEntry = new WeakHashMap<>();

    /** chat_heads' ChatComponentMixin2.prependChatHead, applied to MultiChat's own copy of the
     *  message: same condition (BEFORE_LINE and a sender was found), same call. Without it the head
     *  only exists in the vanilla GuiMessage, which only the All tab renders from. */
    public static Component withBeforeLineHead(Component message) {
        if (!present || !headsInText) return message;
        try {
            if (dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE)
                return message;
            dzwdz.chat_heads.HeadData sender = dzwdz.chat_heads.ChatHeads.lastSenderData;
            if (sender == dzwdz.chat_heads.HeadData.EMPTY) return message;
            return dzwdz.chat_heads.ComponentProcessor.prependChatHead(message, sender.playerInfo());
        } catch (Throwable t) {
            disable(t);
            return message;
        }
    }

    /** Marks the message as one that went through chat_heads' pipeline, so its lines take part in
     *  BEFORE_LINE alignment (MultiChat's own Notes/Info/raw lines never do). 1.3+: every message
     *  is marked, because chat_heads evaluates the offset live per line in any mode. 1.2.x: only
     *  messages that arrived in BEFORE_LINE — a BEFORE_NAME head is already in the text, and a
     *  second, drawn one would be added on a later switch to BEFORE_LINE. */
    public static HeadRef captureLastSenderData() {
        if (!present) return HeadRef.NONE;
        try {
            if (!headsInText && dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE)
                return HeadRef.NONE;
            return new HeadRef(dzwdz.chat_heads.ChatHeads.lastSenderData);
        } catch (Throwable t) {
            disable(t);
            return HeadRef.NONE;
        }
    }

    /** Associates a captured HeadRef with the logical message entry it belongs to. */
    public static void attach(LzChatEntry entry, HeadRef ref) {
        if (!present) return;
        headByEntry.put(entry, ref);
    }

    /** Returns the HeadRef for this entry, or {@link HeadRef#NONE} if it has none. */
    public static HeadRef get(LzChatEntry entry) {
        if (!present || entry == null) return HeadRef.NONE;
        return headByEntry.getOrDefault(entry, HeadRef.NONE);
    }

    // ── Layout ────────────────────────────────────────────────────────────────────────────────

    /** Pixels this wrapped line's text is shifted right. 1.3+: chat_heads' getChatOffset(line) —
     *  {@code headWidth()} when "offset non-player text" applies and the line doesn't start with a
     *  head. 1.2.x: getChatOffset(HeadData) — the head line makes room for the drawn head, other
     *  lines follow "offset non-player text". 0 for lines that never went through chat_heads. */
    public static int lineOffset(LzChatLine line) {
        if (!present || line == null) return 0;
        HeadRef ref = get(line.entry());
        if (ref == HeadRef.NONE) return 0;
        try {
            if (headsInText)
                return offsetEnabled() && !startsWithHead(line.content()) ? dzwdz.chat_heads.ChatHeads.headWidth() : 0;
            return legacyChatOffset(line.startOfEntry() ? ref.data : dzwdz.chat_heads.HeadData.EMPTY);
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /** Wrap-width to reserve on a non-primary panel for an entry, so its widest shifted line still
     *  fits inside the panel's fixed edge. 1.3+: the head itself is a glyph the wrap already counts;
     *  only lines that get shifted need room. 1.2.x: the drawn head on the first line, or the
     *  alignment shift on the others. */
    public static int wrapReserve(LzChatEntry entry) {
        HeadRef ref = get(entry);
        if (ref == HeadRef.NONE) return 0;
        try {
            if (headsInText) return offsetEnabled() ? dzwdz.chat_heads.ChatHeads.headWidth() : 0;
            return Math.max(legacyChatOffset(ref.data), legacyChatOffset(dzwdz.chat_heads.HeadData.EMPTY));
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /** Extra width the primary panel's background/render bound grows by — chat_heads'
     *  ChatComponentMixin.fixTextOverflow, which widens vanilla's line fill by
     *  getTextWidthDifference(). 1.3+: {@code headWidth()} only while lines are being offset.
     *  1.2.x: {@code headWidth()} in BEFORE_LINE. */
    public static int maxHeadWidth() {
        if (!present) return 0;
        try {
            if (headsInText) return offsetEnabled() ? dzwdz.chat_heads.ChatHeads.headWidth() : 0;
            if (dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE)
                return 0;
            return dzwdz.chat_heads.ChatHeads.headWidth();
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    // chat_heads 1.3's private ChatHeads.offsetEnabled().
    private static boolean offsetEnabled() {
        return dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() == dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE
            && dzwdz.chat_heads.ChatHeads.CONFIG.offsetNonPlayerText()
            && !dzwdz.chat_heads.ChatHeads.serverDisabledChatHeads;
    }

    // chat_heads 1.3's private ChatHeads.startsWithHead(line): only the first codepoint counts.
    private static boolean startsWithHead(LzOrderedText content) {
        boolean[] r = new boolean[1];
        content.mc().accept((index, style, codepoint) -> {
            r[0] = style.getFont() instanceof FontDescription.PlayerSprite;
            return false;
        });
        return r[0];
    }

    // chat_heads 1.2.x's ChatHeads.getChatOffset(HeadData) (1.3 replaced it with getChatOffset(line)).
    private static int legacyChatOffset(dzwdz.chat_heads.HeadData data) {
        if (dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE) return 0;
        boolean offset = data != dzwdz.chat_heads.HeadData.EMPTY
            || (dzwdz.chat_heads.ChatHeads.CONFIG.offsetNonPlayerText() && !dzwdz.chat_heads.ChatHeads.serverDisabledChatHeads);
        return offset ? dzwdz.chat_heads.ChatHeads.headWidth() : 0;
    }

    // ── Timestamps ────────────────────────────────────────────────────────────────────────────

    /** If {@code text} starts with a BEFORE_LINE head, splits it off: [head, rest]. Otherwise null.
     *  chat_heads applies after Chat Timestamps on purpose so a line's head stays in front of its
     *  timestamp; MultiChat's own timestamps keep that order through this. */
    public static Component[] splitLeadingHead(Component text) {
        if (!present || !headsInText) return null;
        try {
            if (dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE)
                return null;
            Component[] head = new Component[1];
            Component rest = withoutFirstLeaf(text, head);
            if (head[0] == null || !(head[0].getContents() instanceof ObjectContents) || !isPlayerSprite(head[0]))
                return null;
            return new Component[]{ head[0], rest };
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    // An object node renders as one codepoint whose style font is the object's sprite source — the
    // same thing chat_heads' startsWithHead checks on a wrapped line.
    private static boolean isPlayerSprite(Component node) {
        boolean[] r = new boolean[1];
        node.getVisualOrderText().accept((index, style, codepoint) -> {
            r[0] = style.getFont() instanceof FontDescription.PlayerSprite;
            return false;
        });
        return r[0];
    }

    // Depth-first first leaf with non-empty content (the first thing that renders), removed from a
    // copy of the tree. Styles of the nodes above it stay on the copy; the leaf is returned with its
    // own style. Returns the tree unchanged (and out[0] null) if the first leaf isn't found.
    private static Component withoutFirstLeaf(Component node, Component[] out) {
        if (!(node.getContents() instanceof PlainTextContents plain && plain.text().isEmpty())) {
            out[0] = node.plainCopy().setStyle(node.getStyle());
            MutableComponent copy = MutableComponent.create(PlainTextContents.EMPTY).setStyle(node.getStyle());
            for (Component sibling : node.getSiblings()) copy.append(sibling);
            return copy;
        }
        List<Component> siblings = node.getSiblings();
        if (siblings.isEmpty()) return node;
        MutableComponent copy = MutableComponent.create(PlainTextContents.EMPTY).setStyle(node.getStyle());
        copy.append(withoutFirstLeaf(siblings.get(0), out));
        for (int i = 1; i < siblings.size(); i++) copy.append(siblings.get(i));
        return copy;
    }

    // ── Drawing ───────────────────────────────────────────────────────────────────────────────

    /** Draws a 1.2.x BEFORE_LINE head at (x, y). No-op on 1.3+, where the head is part of the text,
     *  and for a ref with no real head (the alignment-only case). */
    public static void renderHead(GuiGraphicsExtractor context, int x, int y, LzChatLine line, float opacity) {
        if (!present || headsInText || line == null || !line.startOfEntry()) return;
        HeadRef ref = get(line.entry());
        if (ref == HeadRef.NONE) return;
        try {
            if (ref.data == dzwdz.chat_heads.HeadData.EMPTY) return;
            dzwdz.chat_heads.ChatHeads.renderChatHead(context, x, y, ref.data.playerInfo(), opacity);
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** Call immediately before measuring or drawing text that may contain a chat_heads head. */
    public static void beginHeadRendering() {
        if (!present) return;
        if (depth++ > 0) return;
        try {
            dzwdz.chat_heads.ChatHeads.customHeadRendering = true;
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** Always call in a finally paired with {@link #beginHeadRendering()} — never leave the flag on. */
    public static void endHeadRendering() {
        if (!present) return;
        if (--depth > 0) return;
        try {
            dzwdz.chat_heads.ChatHeads.customHeadRendering = false;
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void disable(Throwable t) {
        present = false;
        MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
    }
}
