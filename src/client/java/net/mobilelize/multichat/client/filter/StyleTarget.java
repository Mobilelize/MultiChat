package net.mobilelize.multichat.client.filter;

/** Where in the message a StyleCondition checks the style. WORD searches for a specific word/substring
 *  (see WordLocation for where it's allowed to be found); the other two are simple boundary checks —
 *  does the message start/end with this formatting. */
public enum StyleTarget {
    WORD, START_OF_MESSAGE, END_OF_MESSAGE
}
