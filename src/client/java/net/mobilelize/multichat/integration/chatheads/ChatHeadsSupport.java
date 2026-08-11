package net.mobilelize.multichat.integration.chatheads;

import net.fabricmc.loader.api.FabricLoader;
import net.mobilelize.multichat.client.MultichatClient;

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
