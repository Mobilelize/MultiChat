package net.mobilelize.multichat.client.ping;

import net.mobilelize.fabriclib.client.LzAfk;
import net.mobilelize.fabriclib.client.LzSound;
import net.mobilelize.fabriclib.registry.LzRegistry;
import net.mobilelize.multichat.client.ChatManager;
import net.mobilelize.multichat.client.ChatTab;
import net.mobilelize.multichat.client.MultichatClient;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class PingPlayer {

    private static final Map<ChatTab, Long> lastPlayed      = new WeakHashMap<>();
    private static final Map<ChatTab, Long> lastMessageTime = new WeakHashMap<>();

    public static void playForTab(ChatTab tab) {
        // Track message time regardless of enabled state — silence clock must run always
        long now     = System.currentTimeMillis();
        Long lastMsg = lastMessageTime.get(tab);
        lastMessageTime.put(tab, now);

        if (!MultichatClient.CONFIG.pingEnabled) return;
        if (!tab.pingEnabled()) return;

        String soundId = tab.pingSound().isEmpty()
            ? MultichatClient.CONFIG.pingSound
            : tab.pingSound();
        float volume = tab.pingVolume() < 0
            ? MultichatClient.CONFIG.pingVolume
            : tab.pingVolume();
        float pitch = tab.pingPitch() < 0
            ? MultichatClient.CONFIG.pingPitch
            : tab.pingPitch();
        int cooldownSec = tab.pingCooldownSec() < 0
            ? MultichatClient.CONFIG.pingCooldownSec
            : tab.pingCooldownSec();
        int silenceThresholdSec = tab.pingSilenceThresholdSec() < 0
            ? MultichatClient.CONFIG.pingSilenceThresholdSec
            : tab.pingSilenceThresholdSec();
        int afkThresholdSec = tab.pingAfkThresholdSec() < 0
            ? MultichatClient.CONFIG.pingAfkThresholdSec
            : tab.pingAfkThresholdSec();

        // Resolve effective conditions — tab overrides global when not using global
        boolean useGlobal     = tab.pingUseGlobalConditions();
        boolean condAlways    = useGlobal ? MultichatClient.CONFIG.pingCondAlways         : tab.pingCondAlways();
        boolean condActiveTab = useGlobal ? MultichatClient.CONFIG.pingCondActiveTab      : tab.pingCondActiveTab();
        boolean condUnfocused = useGlobal ? MultichatClient.CONFIG.pingCondUnfocusedTab   : tab.pingCondUnfocusedTab();
        boolean condGame      = useGlobal ? MultichatClient.CONFIG.pingCondGameUnfocused  : tab.pingCondGameUnfocused();
        boolean condAfk       = useGlobal ? MultichatClient.CONFIG.pingCondAfk            : tab.pingCondAfk();
        boolean condSilence   = useGlobal ? MultichatClient.CONFIG.pingCondRequireSilence : tab.pingCondRequireSilence();

        boolean tabVisible = ChatManager.isTabActiveInAnyPanel(tab);
        boolean winFocused = LzSound.isWindowFocused();

        // OR between all enabled conditions
        boolean anyCondition =
            condAlways ||
            (condActiveTab && tabVisible) ||
            (condUnfocused && !tabVisible) ||
            (condGame      && !winFocused) ||
            (condAfk       && LzAfk.isAfk(afkThresholdSec));

        if (!anyCondition) return;

        if (condSilence) {
            boolean silenceMet = lastMsg == null || (now - lastMsg) >= (silenceThresholdSec * 1000L);
            if (!silenceMet) return;
        }

        Long lastPinged = lastPlayed.get(tab);
        if (cooldownSec > 0 && lastPinged != null && (now - lastPinged) < (cooldownSec * 1000L)) return;

        lastPlayed.put(tab, now);
        LzSound.playMaster(soundId, pitch, volume);
    }

    public static Set<String> allSoundIds() {
        return LzRegistry.soundIds();
    }
}
