package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.client.LzChatLine;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NoteTab extends ChatTab {

    public static class NoteEntry {
        private LzText content;
        private int    tick;

        public NoteEntry(LzText content, int tick) {
            this.content = content;
            this.tick    = tick;
        }

        public LzText content() { return content; }
        public int    tick()    { return tick; }
        public void   setContent(LzText v) { content = v; }
    }

    private final List<NoteEntry> noteEntries = new ArrayList<>();

    public NoteTab(String name, Collection<Integer> visibleOnPanels) {
        super(name, TabType.NOTES, visibleOnPanels);
    }

    public List<NoteEntry> noteEntries() { return noteEntries; }

    public void addNote(LzText text, int tick) {
        noteEntries.addFirst(new NoteEntry(text, tick));
        LzChatEntry entry = new LzChatEntry(tick, text, null);
        List<LzOrderedText> lines = breakNote(text);
        for (int j = 0; j < lines.size(); j++)
            addFirst(LzChatLine.create(tick, lines.get(j), null, j == lines.size() - 1, entry));
        clearWidthCaches();
    }

    public void removeNote(int index) {
        if (index < 0 || index >= noteEntries.size()) return;
        noteEntries.remove(index);
        rebuildMessages();
    }

    public void rebuildMessages() {
        clearMessages();
        int width = noteWidth();
        for (int i = noteEntries.size() - 1; i >= 0; i--) {
            NoteEntry note = noteEntries.get(i);
            LzChatEntry entry = new LzChatEntry(note.tick(), note.content(), null);
            List<LzOrderedText> lines = LzChat.breakLines(note.content(), width);
            if (lines.isEmpty()) lines = List.of(note.content().ordered());
            for (int j = 0; j < lines.size(); j++)
                addFirst(LzChatLine.create(note.tick(), lines.get(j), null, j == lines.size() - 1, entry));
        }
        clearWidthCaches();
    }

    private static List<LzOrderedText> breakNote(LzText text) {
        int width = noteWidth();
        List<LzOrderedText> lines = LzChat.breakLines(text, width);
        if (lines.isEmpty()) lines = List.of(text.ordered());
        return lines;
    }

    // Same formula as ChatHudMixin.routeGlobally — float cast matches the mixin's width calculation.
    private static int noteWidth() {
        float scale = (float) LzChat.chatScale();
        return LzMath.floor((double) LzChat.chatWidth() / scale);
    }
}
