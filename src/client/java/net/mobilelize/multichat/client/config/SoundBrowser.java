package net.mobilelize.multichat.client.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.mobilelize.fabriclib.text.LzText;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

public class SoundBrowser {

    // depth → subcategory label color: gold → yellow → aqua → dark aqua → gray
    private static final String[] DEPTH_COLORS = { "&6", "&e", "&b", "&3", "&7" };
    private static final String   LEAF_COLOR   = "&a";

    private static class SoundNode {
        final TreeMap<String, SoundNode> children = new TreeMap<>();
        String soundId = null;
    }

    public static AbstractConfigListEntry<?> build(ConfigEntryBuilder eb, Set<String> soundIds, Consumer<String> onSelect) {
        SoundNode root = buildTree(soundIds);
        SubCategoryBuilder browser = eb.startSubCategory(LzText.format("&fSound Browser").mc());
        renderNode(eb, browser::add, root, onSelect, 0);
        return browser.build();
    }

    private static SoundNode buildTree(Set<String> soundIds) {
        SoundNode root = new SoundNode();
        for (String id : soundIds) {
            int colon    = id.indexOf(':');
            String namespace = colon >= 0 ? id.substring(0, colon) : id;
            String path      = colon >= 0 ? id.substring(colon + 1) : "";
            String[] parts   = path.isEmpty() ? new String[0] : path.split("\\.");
            SoundNode node = root.children.computeIfAbsent(namespace, k -> new SoundNode());
            for (String part : parts)
                node = node.children.computeIfAbsent(part, k -> new SoundNode());
            node.soundId = id;
        }
        return root;
    }

    private static void renderNode(ConfigEntryBuilder eb, Consumer<AbstractConfigListEntry<?>> addTo,
                                   SoundNode node, Consumer<String> onSelect, int depth) {
        String color = DEPTH_COLORS[Math.min(depth, DEPTH_COLORS.length - 1)];
        for (Map.Entry<String, SoundNode> entry : node.children.entrySet()) {
            String label    = entry.getKey();
            SoundNode child = entry.getValue();
            if (child.soundId != null) {
                final String soundId = child.soundId;
                addTo.accept(eb.startBooleanToggle(LzText.format(LEAF_COLOR + label).mc(), false)
                    .setDefaultValue(false)
                    .setTooltip(LzText.format("&7" + soundId).mc())
                    .setSaveConsumer(v -> { if (v) onSelect.accept(soundId); })
                    .build());
            } else {
                SubCategoryBuilder sub = eb.startSubCategory(LzText.format(color + label).mc());
                renderNode(eb, sub::add, child, onSelect, depth + 1);
                addTo.accept(sub.build());
            }
        }
    }
}
