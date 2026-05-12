package net.mobilelize.multichat.client;

import net.mobilelize.multichat.client.render.ChatRenderer;

public class SearchTab extends ChatTab {

    public final String   query;
    public final boolean  useRegex;
    public final boolean  caseSensitive;
    public final int      creationTick;
    public final int      timeFromTicks; // max age in ticks (0 = no max)
    public final int      timeToTicks;   // min age in ticks (0 = no min)
    public final ChatTab  sourceTab;     // null = search all messages
    public       boolean  liveUpdate;

    public SearchTab(String name, String query, boolean useRegex, boolean caseSensitive,
                     int timeFromTicks, int timeToTicks, ChatTab sourceTab, boolean liveUpdate,
                     TabVisibility visibility) {
        super(name, TabType.SEARCH, visibility);
        this.query         = query;
        this.useRegex      = useRegex;
        this.caseSensitive = caseSensitive;
        this.creationTick  = ChatRenderer.currentTick;
        this.timeFromTicks = timeFromTicks;
        this.timeToTicks   = timeToTicks;
        this.sourceTab     = sourceTab;
        this.liveUpdate    = liveUpdate;
    }

    public boolean matchesText(String text) {
        if (query.isEmpty()) return true;
        if (useRegex) {
            int flags = caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE;
            try {
                return java.util.regex.Pattern.compile(query, flags).matcher(text).find();
            } catch (Exception e) {
                return false;
            }
        } else {
            if (caseSensitive) return text.contains(query);
            return text.toLowerCase().contains(query.toLowerCase());
        }
    }

    // timeFromTicks = max age (older messages excluded); 0 = no max
    // timeToTicks   = min age (newer messages excluded); 0 = no min
    // "From: 10m, To: 5m" → messages 5–10 minutes old at creation time
    public boolean matchesTime(int addedTime) {
        if (timeFromTicks == 0 && timeToTicks == 0) return true;
        int age = creationTick - addedTime;
        if (age < 0) return true; // message arrived after search created — always include
        if (timeFromTicks > 0 && age > timeFromTicks) return false;
        if (timeToTicks   > 0 && age < timeToTicks)   return false;
        return true;
    }
}
