package net.mobilelize.multichat.client.filter;

public class FilterRule {

    public final FilterType type;
    public final String value;
    /** Null unless type == MESSAGE_TYPE. */
    public final MessageType messageType;

    public FilterRule(FilterType type, String value) {
        this.type = type;
        this.value = value;
        this.messageType = null;
    }

    public FilterRule(FilterType type, String value, MessageType messageType) {
        this.type = type;
        this.value = value;
        this.messageType = messageType;
    }

    public boolean matches(String text, MessageType msgType) {
        return switch (type) {
            case ALL -> true;
            case KEYWORD -> text.toLowerCase().contains(value.toLowerCase());
            case REGEX -> {
                try { yield text.matches(value); } catch (Exception e) { yield false; }
            }
            case SENDER_NAME -> text.contains("<" + value + ">");
            case MESSAGE_TYPE -> messageType != null && messageType == msgType;
        };
    }

    @Override
    public String toString() {
        if (type == FilterType.MESSAGE_TYPE && messageType != null) {
            return "message_type:" + messageType.name().toLowerCase();
        }
        return type.name().toLowerCase() + ":" + value;
    }
}
