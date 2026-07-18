package net.mobilelize.fabriclib.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mobilelize.fabriclib.client.LzClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Base class for configs. Extend this, add public fields as your config values,
 * and call {@link #load(Class)} to get an instance.
 *
 * <pre>{@code
 * public class MyConfig extends LzConfig {
 *
 *     @Override
 *     public String directory() { return "config/MyMod"; }
 *
 *     // Just add fields — they auto-save and load
 *     public boolean enabled = true;
 *     public String prefix = "&a[MOD]";
 *     public int radius = 4;
 * }
 *
 * // Init:
 * static MyConfig config = LzConfig.load(MyConfig.class);
 *
 * // Save:
 * config.save();
 * }</pre>
 *
 * The config class must have a no-arg constructor (default constructor counts).
 */
public abstract class LzConfig {

    // -------------------------------------------------------------------------
    // Override to customize
    // -------------------------------------------------------------------------

    /**
     * Folder path relative to the game run directory.
     * Default: {@code config/ClassName}
     */
    public String directory() {
        return "config/" + getClass().getSimpleName();
    }

    /**
     * JSON file name without extension.
     * Default: {@code config}
     */
    public String fileName() {
        return "config";
    }

    /**
     * Whether to pretty-print the JSON file.
     * Default: {@code true}
     */
    public boolean prettyPrint() {
        return true;
    }

    /**
     * Character encoding used when reading and writing the file.
     * Default: UTF-8
     */
    public Charset encoding() {
        return StandardCharsets.UTF_8;
    }

    /**
     * Called after this config is successfully loaded or created with defaults.
     * Override to react to a load event (e.g. apply a loaded value immediately).
     */
    protected void onLoad() {}

    /**
     * Called right before this config is written to disk.
     * Override to prepare values before saving (e.g. clamp a field to a valid range).
     */
    protected void onSave() {}

    // -------------------------------------------------------------------------
    // Final — don't override
    // -------------------------------------------------------------------------

    /** The config directory as a {@link File}. */
    public final File dir() {
        return new File(LzClient.runDir(), directory());
    }

    /** The config JSON file as a {@link File}. */
    public final File file() {
        return new File(dir(), fileName() + ".json");
    }

    /** Saves this config to disk. Creates the directory if it doesn't exist. */
    public final void save() {
        onSave();
        try {
            File d = dir();
            if (!d.exists()) d.mkdirs();
            try (FileWriter writer = new FileWriter(file(), encoding())) {
                gson().toJson(this, writer);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Loads the config from disk. If the file doesn't exist, saves defaults and returns them.
     * If loading fails, returns a fresh default instance.
     *
     * @param type  the config class to load
     * @param <T>   must extend {@link LzConfig} and have a no-arg constructor
     */
    public static <T extends LzConfig> T load(Class<T> type) {
        try {
            T defaults = newInstance(type);
            File file = defaults.file();
            if (!file.exists()) {
                defaults.save();
                defaults.onLoad();
                return defaults;
            }
            try (FileReader reader = new FileReader(file, defaults.encoding())) {
                T loaded = defaults.gson().fromJson(reader, type);
                if (loaded != null) {
                    loaded.onLoad();
                    return loaded;
                }
            }
            defaults.onLoad();
            return defaults;
        } catch (Exception e) {
            return newInstance(type);
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    Gson gson() {
        return prettyPrint()
                ? new GsonBuilder().setPrettyPrinting().create()
                : new Gson();
    }

    private static <T extends LzConfig> T newInstance(Class<T> type) {
        try {
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("LzConfig: " + type.getSimpleName() + " must have a no-arg constructor", e);
        }
    }
}
