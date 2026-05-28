package net.mobilelize.multichat.client;

import net.mobilelize.multichat.client.render.ChatRenderer;
import net.mobilelize.multichat.client.search.SearchMode;

public class SearchTab extends ChatTab {

    private final String     query;
    private final SearchMode searchMode;
    private final boolean    caseSensitive;
    private final int        creationTick;
    private final int        timeFromTicks;
    private final int        timeToTicks;
    private final ChatTab    sourceTab;
    private boolean          liveUpdate;
    private boolean          collapseIdentical;

    public SearchTab(String name, String query, SearchMode searchMode, boolean caseSensitive,
                     int timeFromTicks, int timeToTicks, ChatTab sourceTab, boolean liveUpdate,
                     java.util.Collection<Integer> visibleOnPanels) {
        super(name, TabType.SEARCH, visibleOnPanels);
        this.query         = query;
        this.searchMode    = searchMode;
        this.caseSensitive = caseSensitive;
        this.creationTick  = ChatRenderer.currentTick;
        this.timeFromTicks = timeFromTicks;
        this.timeToTicks   = timeToTicks;
        this.sourceTab     = sourceTab;
        this.liveUpdate    = liveUpdate;
    }

    public String     query()           { return query; }
    public SearchMode searchMode()      { return searchMode; }
    public boolean    caseSensitive()   { return caseSensitive; }
    public int        creationTick()    { return creationTick; }
    public int        timeFromTicks()   { return timeFromTicks; }
    public int        timeToTicks()     { return timeToTicks; }
    public ChatTab    sourceTab()       { return sourceTab; }
    public boolean    liveUpdate()      { return liveUpdate; }
    public void       setLiveUpdate(boolean v)        { liveUpdate = v; }
    public boolean    collapseIdentical()             { return collapseIdentical; }
    public void       setCollapseIdentical(boolean v) { collapseIdentical = v; }

    public boolean matchesText(String text) {
        if (query.isEmpty()) return true;
        int flags = caseSensitive ? 0 : java.util.regex.Pattern.CASE_INSENSITIVE;
        return switch (searchMode) {
            case CONTAINS -> caseSensitive ? text.contains(query)
                                           : text.toLowerCase().contains(query.toLowerCase());
            case WORD -> {
                try {
                    yield java.util.regex.Pattern
                        .compile("\\b" + java.util.regex.Pattern.quote(query) + "\\b", flags)
                        .matcher(text).find();
                } catch (Exception e) { yield false; }
            }
            case FUZZY -> {
                String t = caseSensitive ? text  : text.toLowerCase();
                String q = caseSensitive ? query : query.toLowerCase();
                if (q.isEmpty()) yield true;
                int qi = 0;
                for (int ti = 0; ti < t.length() && qi < q.length(); ti++)
                    if (t.charAt(ti) == q.charAt(qi)) qi++;
                yield qi == q.length();
            }
            case REGEX -> {
                try {
                    yield java.util.regex.Pattern.compile(query, flags).matcher(text).find();
                } catch (Exception e) { yield false; }
            }
        };
    }

    public boolean matchesTime(int addedTime) {
        if (timeFromTicks == 0 && timeToTicks == 0) return true;
        int age = creationTick - addedTime;
        if (age < 0) return true;
        if (timeFromTicks > 0 && age > timeFromTicks) return false;
        if (timeToTicks   > 0 && age < timeToTicks)   return false;
        return true;
    }
}
