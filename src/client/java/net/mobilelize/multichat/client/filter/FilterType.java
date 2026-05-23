package net.mobilelize.multichat.client.filter;

public enum FilterType {
    CONTAINS,     // case-insensitive substring
    STARTS_WITH,  // case-insensitive prefix
    ENDS_WITH,    // case-insensitive suffix
    EXACT,        // case-insensitive exact match
    WORD,         // whole-word match (case-insensitive)
    FUZZY,        // subsequence match (all query chars appear in order, case-insensitive)
    REGEX,        // regex anywhere in message
    REGEX_START,  // regex anchored to start
    REGEX_END     // regex anchored to end
}
