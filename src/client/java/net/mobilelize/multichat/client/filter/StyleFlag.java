package net.mobilelize.multichat.client.filter;

/** A single boolean rendering style. A StyleCondition can require several at once (all must be present) —
 *  see StyleCondition.flags(). Color is handled separately by StyleColor, since it's a value, not a flag.
 *  The CLICK_ and HOVER_ flags check for a specific interaction type rather than plain formatting — useful
 *  as an anti-spoof signal, since a player typing plain chat can't attach a real click/hover event to it,
 *  only server-formatted text can. HAS_CLICK/HAS_HOVER match any interaction of that kind, regardless of type. */
public enum StyleFlag {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, OBFUSCATED,
    HAS_CLICK, HAS_HOVER,
    CLICK_RUN_COMMAND, CLICK_SUGGEST_COMMAND, CLICK_OPEN_URL, CLICK_COPY_TO_CLIPBOARD,
    HOVER_SHOW_TEXT
}
