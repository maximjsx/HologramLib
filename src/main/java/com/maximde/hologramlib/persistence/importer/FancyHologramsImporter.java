package com.maximde.hologramlib.persistence.importer;

import com.maximde.hologramlib.persistence.ArgbColor;
import com.maximde.hologramlib.persistence.HologramData;
import com.maximde.hologramlib.utils.Enums;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Reads {@code plugins/FancyHolograms/holograms.yml}
 */
public final class FancyHologramsImporter extends HologramImporter {

    private static final String VIEW_PERMISSION_PREFIX = "fancyholograms.viewhologram.";
    private static final int TRANSPARENT = 0;
    private static final int OPAQUE = 0xFF000000;

    public FancyHologramsImporter(Path pluginsFolder, Logger logger) {
        super("FancyHolograms", pluginsFolder.resolve("FancyHolograms").resolve("holograms.yml"), logger);
    }

    @Override
    protected Optional<Result> readAll() {
        if (!Files.isRegularFile(getSource())) return Optional.empty();
        ConfigurationSection holograms = YamlConfiguration.loadConfiguration(getSource().toFile()).getConfigurationSection("holograms");
        if (holograms == null) return Optional.empty();

        List<HologramData> imported = new ArrayList<>();
        int skipped = 0;
        for (String name : holograms.getKeys(false)) {
            Optional<HologramData> converted = Optional.ofNullable(holograms.getConfigurationSection(name))
                    .flatMap(section -> convert(name, section));
            if (converted.isEmpty()) {
                logSkipped(name);
                skipped++;
            }
            converted.ifPresent(imported::add);
        }
        return Optional.of(new Result(imported, skipped));
    }

    private static Optional<HologramData> convert(String name, ConfigurationSection section) {
        Optional<HologramData.Type> type = Enums.parse(HologramData.Type.class, section.getString("type", ""));
        String world = section.getString("location.world");
        if (type.isEmpty() || world == null) return Optional.empty();
        return content(type.get(), section).map(content -> new HologramData(
                validId(name),
                type.get(),
                world,
                // translation is relative to the display, adding it straight is only off for rotated x and z offsets
                section.getDouble("location.x") + section.getDouble("translation_x"),
                section.getDouble("location.y") + section.getDouble("translation_y"),
                section.getDouble("location.z") + section.getDouble("translation_z"),
                (float) section.getDouble("location.yaw"),
                content,
                new HologramData.Scale((float) section.getDouble("scale_x", 1), (float) section.getDouble("scale_y", 1),
                        (float) section.getDouble("scale_z", 1)),
                Enums.parse(Display.Billboard.class, section.getString("billboard", "")).orElse(Display.Billboard.CENTER),
                background(section.getString("background")),
                section.getBoolean("text_shadow"),
                content.contains("%"),
                hiddenByDefault(section) ? VIEW_PERMISSION_PREFIX + name : null,
                List.of()));
    }

    private static Optional<String> content(HologramData.Type type, ConfigurationSection section) {
        return switch (type) {
            case TEXT -> Optional.of(String.join("<newline>", section.getStringList("text")));
            case ITEM -> Optional.ofNullable(section.getItemStack("item")).map(ItemStack::getType).map(FancyHologramsImporter::key);
            case BLOCK -> Optional.ofNullable(Material.matchMaterial(section.getString("block", ""))).map(FancyHologramsImporter::key);
        };
    }

    private static String key(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    private static boolean hiddenByDefault(ConfigurationSection section) {
        String visibility = section.getString("visibility");
        if (visibility == null) return !section.getBoolean("visible_by_default", true);
        return !visibility.equalsIgnoreCase("ALL");
    }

    private static @Nullable Integer background(@Nullable String background) {
        if (background == null) return null;
        if (background.equalsIgnoreCase("transparent")) return TRANSPARENT;
        if (background.startsWith("#")) return ArgbColor.parse(padded(background.substring(1))).orElse(null);
        NamedTextColor named = NamedTextColor.NAMES.value(background.toLowerCase(Locale.ROOT).replace(' ', '_'));
        return named == null ? null : OPAQUE | named.value();
    }
    private static String padded(String hex) {
        return hex.length() == 7 ? "0" + hex : hex;
    }
}
