package net.ponti.miningdropper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class DropConfig {
    private static final Set<Item> LOOSE_BY_DEFAULT = Set.of(
            Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD, Items.QUARTZ);
    private final Path file;
    private final Properties settings = new Properties();

    DropConfig(Path file) throws IOException {
        this.file = file;
        try (var reader = Files.newBufferedReader(file)) {
            settings.load(reader);
        } catch (NoSuchFileException firstRun) {
            // Defaults live in the catalog until the first setting is changed.
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid properties in " + file, malformed);
        }
    }

    boolean drops(DropRules.Rule rule) {
        return get(rule.key(), rule.dropByDefault());
    }

    boolean scansHotbar() { return get("scan_hotbar", true); }

    boolean compacts() { return get("auto_compact", true); }

    static String compactKey(Item material) {
        return "compact_" + BuiltInRegistries.ITEM.getKey(material).getPath();
    }

    boolean compacts(Item material) {
        return get(compactKey(material), !LOOSE_BY_DEFAULT.contains(material));
    }

    private boolean get(String key, boolean fallback) {
        return Boolean.parseBoolean(settings.getProperty(key, String.valueOf(fallback)));
    }

    void set(String key, boolean value) throws IOException {
        Properties next = new Properties();
        next.putAll(settings);
        next.setProperty(key, String.valueOf(value));
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "miningdropper-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp)) {
                next.store(writer, "Mining Dropper. true selects DROP for an item rule.");
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            settings.setProperty(key, String.valueOf(value));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
