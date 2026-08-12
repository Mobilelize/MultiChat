package net.mobilelize.multichat.integration.chatheads;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.multichat.client.MultichatClient;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Compatibility shim for dzwdz/chat_heads. Only this class touches chat_heads classes —
 * MultiChat internals must never import dzwdz.chat_heads.* directly.
 * <p>
 * chat_heads' default {@code renderPosition = BEFORE_NAME} bakes the head as a real vanilla
 * {@code ObjectTextContent} node inside the message {@code Text} — nothing to render or
 * suppress here, MultiChat's normal text drawing already paints it. The one thing that
 * doesn't come for free: {@code dzwdz.chat_heads.ChatHeads.customHeadRendering} is a public
 * static flag chat_heads' own glyph-provider mixins ({@code PaddedChatGlyph},
 * {@code PlayerGlyphProviderInstanceMixin}) read to decide whether to reserve/draw the extra
 * padding their "3D-ness" head effect needs. It's only ever flipped {@code true} by chat_heads'
 * own mixins bracketing specific render methods it knows about (vanilla {@code ChatComponent},
 * plus its own opt-in {@code render_targets} list for GuiRenderer/books/Cloth Config) — never
 * for MultiChat's separate render path, so the glyph reports too-narrow an advance there and
 * the head visually overlaps/squishes into the next character. This mirrors that same
 * bracket-a-render-target pattern for MultiChat's own line width/draw calls.
 */
public final class ChatHeadsSupport {

    private static volatile boolean present = detect();

    // Callers (ChatPanel's wrap, ChatRenderer's draw loop, ChatSelection's click/highlight math)
    // each bracket their own width-sensitive work independently and can nest (e.g. selection
    // math running inside a render pass). A plain boolean would let an inner endHeadRendering()
    // turn the flag off while an outer caller is still relying on it — depth-count instead so
    // only the outermost begin/end pair actually flips dzwdz's flag. Single-threaded render/tick,
    // no synchronization needed.
    private static int depth = 0;

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
     *  not whenever the first chat line happens to render. Detection itself is lazy-safe
     *  either way (isModLoaded only reads Fabric's static mod list) — this just moves *when*
     *  it's first touched. */
    public static void init() {}

    public static boolean isPresent() { return present; }

    // ── BEFORE_LINE support ──────────────────────────────────────────────────
    //
    // chat_heads' non-default renderPosition = BEFORE_LINE mode embeds nothing in the message
    // Text at all — instead ChatListenerMixin sets the static ChatHeads.lastSenderData right
    // before vanilla ChatComponent.addMessage(...) runs (the exact method ChatHudMixin.captureRaw
    // already hooks at HEAD, same call stack, so capturing it there is race-free), and chat_heads'
    // own mixins transfer that onto the first wrapped GuiMessage.Line of the message — objects
    // MultiChat never creates. So MultiChat has to capture the head data itself at message-add
    // time, remember which LzChatEntry it belongs to, and do chat_heads' own draw+offset job
    // (ChatHeads.renderChatHead / getChatOffset) manually for its own LzChatLines.

    /** Opaque handle to a chat_heads HeadData — lets code outside this package hold a reference
     *  without importing chat_heads types. {@link #NONE} means "no head for this message". */
    public static final class HeadRef {
        public static final HeadRef NONE = new HeadRef(null);
        private final dzwdz.chat_heads.HeadData data;
        private HeadRef(dzwdz.chat_heads.HeadData data) { this.data = data; }
    }

    // Self-cleans as entries scroll out of every tab's history and become unreachable — no
    // explicit trim/removal hook needed. Single-threaded render/tick, no synchronization needed.
    private static final Map<LzChatEntry, HeadRef> headByEntry = new WeakHashMap<>();

    /** Call from ChatHudMixin.captureRaw, at HEAD of ChatHud.addMessage — same instant chat_heads'
     *  own ChatListenerMixin has just set ChatHeads.lastSenderData for this exact message. Always
     *  wraps whatever value that is (including HeadData.EMPTY, for a message with no real head) —
     *  NOT pre-filtered by whether it currently produces a nonzero offset. headWidth()/renderHead()
     *  re-derive that live from current config on every call, so a config change (e.g. toggling
     *  "offset non-player text") takes effect instantly for every message already in history, the
     *  same way real chat_heads re-evaluates it fresh every render frame. Pre-filtering here would
     *  freeze that decision at message-arrival time and never revisit it. */
    public static HeadRef captureLastSenderData() {
        if (!present) return HeadRef.NONE;
        try {
            return new HeadRef(dzwdz.chat_heads.ChatHeads.lastSenderData);
        } catch (Throwable t) {
            present = false;
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
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

    /** Pixels of leading space to reserve for the head on this entry's first wrapped line. 0 if none. */
    public static int headWidth(HeadRef ref) {
        if (!present || ref == HeadRef.NONE) return 0;
        try {
            return dzwdz.chat_heads.ChatHeads.getChatOffset(ref.data);
        } catch (Throwable t) {
            present = false;
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
            return 0;
        }
    }

    /** Offset for a line that never carries head data of its own — every continuation line of a
     *  wrapped multi-line message (real chat_heads resets its per-line head data to EMPTY right
     *  after the first line consumes it, unconditionally, whether or not that entry had a real
     *  head), and effectively also the first line of any entry with no head at all (headWidth of
     *  a ref wrapping HeadData.EMPTY reduces to exactly this). Purely driven by chat_heads' own
     *  "offset non-player text" alignment setting — when that's on, every line indents to stay
     *  flush with lines that do have a real head; when it's off, only actual head lines indent. */
    public static int emptyLineWidth() {
        if (!present) return 0;
        try {
            return dzwdz.chat_heads.ChatHeads.getChatOffset(dzwdz.chat_heads.HeadData.EMPTY);
        } catch (Throwable t) {
            present = false;
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
            return 0;
        }
    }

    /** Draws the head texture at (x, y). No-op if ref is {@link HeadRef#NONE} or carries no real
     *  head (a nonzero {@link #headWidth} can still happen with no head to draw — chat_heads'
     *  "offset non-player text" alignment option reserves the space without a texture to fill
     *  it, matching real chat_heads' own {@code headData != HeadData.EMPTY} guard). */
    public static void renderHead(DrawContext context, int x, int y, HeadRef ref, float opacity) {
        if (!present || ref == HeadRef.NONE) return;
        try {
            if (ref.data == dzwdz.chat_heads.HeadData.EMPTY) return;
            dzwdz.chat_heads.ChatHeads.renderChatHead(context, x, y, ref.data.playerInfo(), opacity);
        } catch (Throwable t) {
            present = false;
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
        }
    }

    /** Call immediately before measuring/wrapping or drawing text that may contain a chat_heads object. */
    public static void beginHeadRendering() {
        if (!present) return;
        if (depth++ > 0) return;
        try {
            dzwdz.chat_heads.ChatHeads.customHeadRendering = true;
        } catch (Throwable t) {
            present = false;
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
        }
    }

    /** Always call in a finally paired with {@link #beginHeadRendering()} — never leave the flag on. */
    public static void endHeadRendering() {
        if (!present) return;
        if (--depth > 0) return;
        try {
            dzwdz.chat_heads.ChatHeads.customHeadRendering = false;
        } catch (Throwable t) {
            present = false;
            MultichatClient.LOGGER.warn("[MultiChat] Chat Heads compatibility disabled after a failed call", t);
        }
    }
}
