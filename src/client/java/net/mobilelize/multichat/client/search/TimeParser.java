package net.mobilelize.multichat.client.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeParser {

    private static final Pattern TOKEN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*(h(?:ours?)?|m(?:in(?:utes?)?)?|s(?:ec(?:onds?)?)?)?",
        Pattern.CASE_INSENSITIVE);

    // Parses strings like "5m", "10min", "1h30m", "30s", "2.5m" → ticks (20/s).
    // No unit defaults to minutes. Empty or invalid → 0.
    public static int parseTicks(String input) {
        if (input == null || input.isBlank()) return 0;
        Matcher m = TOKEN.matcher(input.trim());
        int total = 0;
        while (m.find()) {
            String numStr = m.group(1);
            String unit   = m.group(2);
            if (numStr == null) continue;
            double val = Double.parseDouble(numStr);
            if (unit == null || unit.isEmpty()) {
                total += (int)(val * 60 * 20); // no unit → minutes
            } else {
                char u = Character.toLowerCase(unit.charAt(0));
                total += switch (u) {
                    case 'h' -> (int)(val * 3600 * 20);
                    case 'm' -> (int)(val *   60 * 20);
                    case 's' -> (int)(val        * 20);
                    default  -> (int)(val *   60 * 20);
                };
            }
        }
        return total;
    }
}
