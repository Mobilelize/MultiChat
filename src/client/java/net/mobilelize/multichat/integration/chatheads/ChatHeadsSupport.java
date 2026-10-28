package net.mobilelize.multichat.integration.chatheads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.multichat.client.MultichatClient;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Compatibility shim for dzwdz/chat_heads. Only this class touches chat_heads classes —
 * MultiChat internals must never import dzwdz.chat_heads.* directly.
 * <p>
 * MC-VERSION-SENSITIVE: built against chat_heads 0.15.x (github.com/dzwdz/chat_heads, tags
 * 0.15.6-1.21.6 / 0.15.6-1.21.5 / 0.15.6-1.21.2 / 0.15.7-1.21), the line used up to MC 1.21.8.
 * It works differently from 1.3.x (MC 1.21.9+), where heads are part of the text:
 * <ul>
 *   <li>Heads are never in the message text. chat_heads keeps the sender's HeadData on the first
 *       wrapped line (GuiMessageLineMixin) and draws the head itself while vanilla chat renders.</li>
 *   <li>BEFORE_LINE (ChatComponentMixin.chatheads$renderChatHead / moveText): head drawn at the
 *       line start, text shifted by {@code getChatOffset(headData)}.</li>
 *   <li>BEFORE_NAME (FontPreparedTextBuilderMixin / FontStringRenderOutputMixin): while
 *       {@code ChatHeads.renderHeadData} is set, the font draws the head when it reaches the name's
 *       codepoint index and moves the rest of the line right by {@code headWidth()}. The index is
 *       found by scanning the wrapped text (ChatComponentMixin2.chatheads$updateHeadPosition).</li>
 *   <li>Every message with a head, or any offset line, wraps narrower by
 *       {@code getTextWidthDifference(headData)} (ChatComponentMixin.chatheads$fixTextOverflow) —
 *       the chat box itself never grows.</li>
 * </ul>
 * MultiChat does all of the above for its own lines through chat_heads' own static methods.
 */
public final class ChatHeadsSupport {

    private static volatile boolean present = detect();

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

    /** Call once from {@code onInitializeClient} so the detect log line lands at startup,
     *  not whenever the first chat line happens to render. */
    public static void init() {}

    public static boolean isPresent() { return present; }

    // Everything that decides wrap-time widths, sampled once a tick by pollLayoutChanged(). chat_heads'
    // config screen has no save callback we can hook, so a change is only noticed by comparing. Render
    // offsets read config live; only widths baked into already-wrapped lines need a rebuild.
    private static String lastLayoutKey = null;

    /** Call once a tick (see MultichatClient). True the moment render position, "offset non-player
     *  text" or the head width (drop shadow) changed since last tick — callers rebuild every panel's
     *  wrapped lines (ChatManager.resetForRefresh()). Never fires on the first call. */
    public static boolean pollLayoutChanged() {
        if (!present) return false;
        try {
            String key = dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() + "|"
                + dzwdz.chat_heads.ChatHeads.CONFIG.offsetNonPlayerText() + "|"
                + dzwdz.chat_heads.ChatHeads.serverDisabledChatHeads + "|" + dzwdz.chat_heads.ChatHeads.headWidth();
            boolean changed = lastLayoutKey != null && !key.equals(lastLayoutKey);
            lastLayoutKey = key;
            return changed;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    // ── Capture (ChatHudMixin.captureRaw, HEAD of ChatHud.addMessage) ────────────────────────────
    //
    // chat_heads' ChatListenerMixin sets ChatHeads.lastSenderData right before vanilla addMessage
    // runs and ChatComponentMixin2 resets it at addMessage's RETURN, so at HEAD it belongs to exactly
    // this message — the same value chat_heads copies onto the message's first GuiMessage.Line.

    /** Opaque handle to a chat_heads HeadData — lets code outside this package hold a reference
     *  without importing chat_heads types. {@link #NONE} means "never went through chat_heads". */
    public static final class HeadRef {
        public static final HeadRef NONE = new HeadRef(null);
        private final dzwdz.chat_heads.HeadData data;
        private HeadRef(dzwdz.chat_heads.HeadData data) { this.data = data; }
    }

    // Self-cleans as entries scroll out of every tab's history and become unreachable.
    private static final Map<LzChatEntry, HeadRef> headByEntry = new WeakHashMap<>();

    /** The sender's head data for the message being added, in any render position (both modes draw
     *  from it here, and a mode switch applies to existing lines, as in vanilla chat). */
    public static HeadRef captureLastSenderData() {
        if (!present) return HeadRef.NONE;
        try {
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

    // chat_heads' per-line head data: the message's on its first wrapped line, EMPTY on the rest
    // (GuiMessageLineMixin resets lineData after the first Line). null = not a chat_heads line.
    private static dzwdz.chat_heads.HeadData lineData(LzChatLine line) {
        if (line == null) return null;
        HeadRef ref = get(line.entry());
        if (ref == HeadRef.NONE) return null;
        return line.startOfEntry() ? ref.data : dzwdz.chat_heads.HeadData.EMPTY;
    }

    // ── Layout ────────────────────────────────────────────────────────────────────────────────

    /** Pixels this line's text starts to the right — chat_heads' getChatOffset(headData), applied by
     *  ChatComponentMixin.chatheads$moveText (BEFORE_LINE only; 0 in BEFORE_NAME). */
    public static int lineOffset(LzChatLine line) {
        if (!present) return 0;
        dzwdz.chat_heads.HeadData data = lineData(line);
        if (data == null) return 0;
        try {
            return dzwdz.chat_heads.ChatHeads.getChatOffset(data);
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /** Pixels this line is wider than its text — chat_heads' getTextWidthDifference(headData): the
     *  drawn head (either mode) or the alignment shift. Vanilla moves the tag icon by it
     *  (chatheads$moveTagIcon). */
    public static int lineWidthExtra(LzChatLine line) {
        if (!present) return 0;
        dzwdz.chat_heads.HeadData data = lineData(line);
        if (data == null) return 0;
        try {
            return dzwdz.chat_heads.ChatHeads.getTextWidthDifference(data);
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /** Wrap-width to take off an entry, on every panel — chat_heads' chatheads$fixTextOverflow, which
     *  wraps the whole message at {@code getWidth() - getTextWidthDifference(lineData)}. */
    public static int wrapReserve(LzChatEntry entry) {
        HeadRef ref = get(entry);
        if (ref == HeadRef.NONE) return 0;
        try {
            return dzwdz.chat_heads.ChatHeads.getTextWidthDifference(ref.data);
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    /** Extra width the primary panel grows by. 0: chat_heads 0.15 narrows the wrap instead of widening
     *  the chat (see wrapReserve). */
    public static int maxHeadWidth() {
        return 0;
    }

    // ── BEFORE_NAME head position ─────────────────────────────────────────────────────────────

    // Head codepoint index per first line, found once the way chatheads$updateHeadPosition does at
    // wrap time. Main thread only (render/selection), keyed by the wrapped line object.
    private static final Map<LzChatLine, Integer> nameIndexByLine = new WeakHashMap<>();

    /** In BEFORE_NAME, the codepoint index on this line where chat_heads' font hook draws the head
     *  (then shifts the rest of the line by {@code headWidth()}). -1 if no head is drawn on it. */
    public static int nameHeadIndex(LzChatLine line) {
        if (!present) return -1;
        dzwdz.chat_heads.HeadData data = lineData(line);
        if (data == null || data == dzwdz.chat_heads.HeadData.EMPTY) return -1;
        try {
            if (dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_NAME)
                return -1;
            int index = nameIndexByLine.computeIfAbsent(line, l -> findNameIndex(l, data));
            return index >= codepointCount(line) ? -1 : index;
        } catch (Throwable t) {
            disable(t);
            return -1;
        }
    }

    /** Pixels the BEFORE_NAME head takes inside a line — chat_heads' headWidth(). */
    public static int nameHeadWidth() {
        try {
            return dzwdz.chat_heads.ChatHeads.headWidth();
        } catch (Throwable t) {
            disable(t);
            return 0;
        }
    }

    // chatheads$updateHeadPosition: scan the text for this player's name; if it's not found, keep
    // the index chat_heads detected originally. The font hook draws at max(index, 0).
    private static int findNameIndex(LzChatLine line, dzwdz.chat_heads.HeadData data) {
        var connection = MinecraftClient.getInstance().getNetworkHandler();
        if (connection != null) {
            var cache = new dzwdz.chat_heads.ChatHeads.PlayerInfoCache(connection);
            cache.add(data.playerInfo());
            StringBuilder text = new StringBuilder();
            line.content().mc().accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
            dzwdz.chat_heads.HeadData found = dzwdz.chat_heads.ChatHeads.scanForPlayerName(text.toString(), cache);
            if (found.hasHeadPosition()) return found.codePointIndex();
        }
        return Math.max(data.codePointIndex(), 0);
    }

    private static int codepointCount(LzChatLine line) {
        int[] n = {0};
        line.content().mc().accept((index, style, codepoint) -> { n[0]++; return true; });
        return n[0];
    }

    // ── Drawing ───────────────────────────────────────────────────────────────────────────────

    /** BEFORE_LINE: draws the head at (x, y), as chatheads$renderChatHead does. No-op otherwise. */
    public static void renderHead(DrawContext context, int x, int y, LzChatLine line, float opacity) {
        if (!present) return;
        dzwdz.chat_heads.HeadData data = lineData(line);
        if (data == null || data == dzwdz.chat_heads.HeadData.EMPTY) return;
        try {
            if (dzwdz.chat_heads.ChatHeads.CONFIG.renderPosition() != dzwdz.chat_heads.config.RenderPosition.BEFORE_LINE)
                return;
            dzwdz.chat_heads.ChatHeads.renderChatHead(context, x, y, data.playerInfo(), opacity);
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** BEFORE_NAME: call right before drawing this line's text, and {@link #endLineText()} right
     *  after — sets the statics chat_heads' font hook reads, as chatheads$renderChatHead does around
     *  vanilla's drawString. The head is then drawn by chat_heads itself. */
    public static void beginLineText(DrawContext context, LzChatLine line, float opacity) {
        int index = nameHeadIndex(line);
        if (index < 0) return;
        try {
            dzwdz.chat_heads.ChatHeads.guiGraphics = context;
            dzwdz.chat_heads.ChatHeads.renderHeadData = new dzwdz.chat_heads.HeadData(lineData(line).playerInfo(), index);
            dzwdz.chat_heads.ChatHeads.renderHeadOpacity = opacity;
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** Pair of {@link #beginLineText} — chatheads$forgetRenderData. */
    public static void endLineText() {
        if (!present) return;
        try {
            dzwdz.chat_heads.ChatHeads.guiGraphics = null;
            dzwdz.chat_heads.ChatHeads.renderHeadData = dzwdz.chat_heads.HeadData.EMPTY;
        } catch (Throwable t) {
            disable(t);
        }
    }

    /** No-op on chat_heads 0.15 (no glyph heads, no customHeadRendering flag). Kept so callers stay
     *  identical across branches. */
    public static void beginHeadRendering() {}

    /** Pair of {@link #beginHeadRendering()}. */
    public static void endHeadRendering() {}

    private static void disable(Throwable t) {
        present = false;
        MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
    }
}
