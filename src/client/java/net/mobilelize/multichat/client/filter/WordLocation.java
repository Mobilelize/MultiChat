package net.mobilelize.multichat.client.filter;

/** Only meaningful when StyleCondition's target is WORD — where the word is allowed to be found.
 *  ANYWHERE: a plain substring search anywhere in the message (word is required).
 *  START: the word must be the message's first token — leave word blank to just check whatever the
 *         first word happens to be, without caring what it is.
 *  END: same as START, but the last token.
 *  INDEX: the word must be at StyleCondition.wordIndex (1-based — 1 = first word, 2 = second, etc.) —
 *         leave word blank to just check whatever word is at that position, same as START/END. */
public enum WordLocation {
    ANYWHERE, START, END, INDEX
}
