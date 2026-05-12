package net.mobilelize.multichat.client.filter;

public enum FilterType {
    ALL,          // matches everything
    CONTAINS,     // case-insensitive substring
    STARTS_WITH,  // case-insensitive prefix
    ENDS_WITH,    // case-insensitive suffix
    EXACT,        // case-insensitive exact match
    WORD,         // whole-word match (case-insensitive)
    REGEX,        // regex pattern
    SENDER,       // matches <SenderName>
    INDICATOR     // matches by MessageIndicator loggedName (or "player" for null)
}
