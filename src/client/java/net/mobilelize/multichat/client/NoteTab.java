package net.mobilelize.multichat.client;

import net.mobilelize.fabriclib.client.LzChat;
import net.mobilelize.fabriclib.client.LzChatEntry;
import net.mobilelize.fabriclib.math.LzMath;
import net.mobilelize.fabriclib.text.LzOrderedText;
import net.mobilelize.fabriclib.text.LzText;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NoteTab extends ChatTab {

    public static class NoteEntry {
        private String contentJson = "";
        private String rawFormat   = "";
        private int    tick        = 0;
        private transient LzText content;

        public NoteEntry() {}

        public NoteEntry(LzText content, int tick) {
            this.content     = content;
            this.contentJson = MultichatClient.textToJson(content);
            this.tick        = tick;
        }

        public LzText  content()            { return content; }
        public int     tick()               { return tick; }
        public String  rawFormat()          { return rawFormat; }
        public String  contentJson()        { return contentJson; }

        public void setContent(LzText v)    { content = v; contentJson = MultichatClient.textToJson(v); }
        public void setRawFormat(String v)  { rawFormat = v; }

        public void restoreContent() {
            if (content == null && !contentJson.isEmpty())
                content = MultichatClient.textFromJson(contentJson);
        }
    }

    private final List<NoteEntry> noteEntries = new ArrayList<>();
    private boolean renderOutOfFocus = true;

    public NoteTab(String name, Collection<Integer> visibleOnPanels) {
        super(name, TabType.NOTES, visibleOnPanels);
    }

    public List<NoteEntry> noteEntries()          { return noteEntries; }
    public boolean renderOutOfFocus()             { return renderOutOfFocus; }
    public void setRenderOutOfFocus(boolean v)    { renderOutOfFocus = v; }

    public void addNote(LzText text, int tick) {
        NoteEntry entry = new NoteEntry(text, tick);
        noteEntries.addFirst(entry);
        LzChatEntry chatEntry = new LzChatEntry(tick, text, null);
        ChatTab.MessageEntry me = new ChatTab.MessageEntry(text, text, chatEntry);
        ChatManager.addNewMessage(this, me);
    }

    public void removeNote(int index) {
        if (index < 0 || index >= noteEntries.size()) return;
        noteEntries.remove(index);
        rebuildMessages();
    }

    public void rebuildMessages() {
        clearMessages();
        for (ChatPanel panel : ChatManager.getAllActivePanels())
            panel.clearTabLines(this);

        for (int i = noteEntries.size() - 1; i >= 0; i--) {
            NoteEntry note = noteEntries.get(i);
            if (note.content() == null) note.restoreContent();
            if (note.content() == null) continue;
            LzChatEntry chatEntry = new LzChatEntry(note.tick(), note.content(), null);
            ChatTab.MessageEntry me = new ChatTab.MessageEntry(note.content(), note.content(), chatEntry);
            addFirst(me);
            for (ChatPanel panel : ChatManager.getAllActivePanels())
                panel.addTabMessage(this, me);
        }
    }
}
