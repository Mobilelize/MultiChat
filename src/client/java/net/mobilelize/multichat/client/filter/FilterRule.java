package net.mobilelize.multichat.client.filter;

import net.minecraft.client.gui.hud.MessageIndicator;

public class FilterRule {

    public final FilterType type;
    public final String value;

    public FilterRule(FilterType type, String value) {
        this.type = type;
        this.value = value;
    }

    public boolean matches(String text, MessageIndicator indicator) {
        String lower = text.toLowerCase();
        String val   = value.toLowerCase();
        return switch (type) {
            case ALL         -> true;
            case CONTAINS    -> lower.contains(val);
            case STARTS_WITH -> lower.startsWith(val);
            case ENDS_WITH   -> lower.endsWith(val);
            case EXACT       -> lower.equals(val);
            case WORD        -> java.util.regex.Pattern.compile(
                                    "\\b" + java.util.regex.Pattern.quote(value) + "\\b",
                                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text).find();
            case REGEX       -> { try { yield text.matches(value); } catch (Exception e) { yield false; } }
            case SENDER      -> text.contains("<" + value + ">");
            case INDICATOR   -> {
                if (val.equals("player")) yield indicator == null;
                if (indicator == null) yield false;
                String loggedName = indicator.loggedName();
                yield val.replace("_", " ").equalsIgnoreCase(loggedName);
            }
        };
    }

    @Override
    public String toString() {
        return type == FilterType.ALL ? "all" : type.name().toLowerCase() + ":" + value;
    }
}
