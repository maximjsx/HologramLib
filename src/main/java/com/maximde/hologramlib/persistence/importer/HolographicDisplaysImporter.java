package com.maximde.hologramlib.persistence.importer;

import com.maximde.hologramlib.persistence.HologramData;
import com.maximde.hologramlib.persistence.importer.StackedLines.Kind;
import com.maximde.hologramlib.persistence.importer.StackedLines.Line;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reads {@code plugins/HolographicDisplays/database.yml} of version 2 and 3
 */
public final class HolographicDisplaysImporter extends HologramImporter {

    private static final String ICON_PREFIX = "icon:";
    private static final String EMPTY_LINE = "{empty}";
    private static final Pattern PAPI_PLACEHOLDER = Pattern.compile("\\{papi:\\s*([^}]+?)\\s*}");
    // the text and item line heights of Holographic Displays plus its default space between lines
    private static final double TEXT_HEIGHT = 0.25;
    private static final double ICON_HEIGHT = 0.62;

    public HolographicDisplaysImporter(Path pluginsFolder, Logger logger) {
        super("HolographicDisplays", pluginsFolder.resolve("HolographicDisplays").resolve("database.yml"), logger);
    }

    @Override
    protected Optional<Result> readAll() {
        if (!Files.isRegularFile(getSource())) return Optional.empty();
        YamlConfiguration database = YamlConfiguration.loadConfiguration(getSource().toFile());
        List<HologramData> holograms = new ArrayList<>();
        int skipped = 0;
        for (String name : database.getKeys(false)) {
            List<HologramData> converted = Optional.ofNullable(database.getConfigurationSection(name))
                    .map(section -> convert(validId(name), section))
                    .orElse(List.of());
            if (converted.isEmpty()) {
                logSkipped(name);
                skipped++;
            }
            holograms.addAll(converted);
        }
        return Optional.of(new Result(holograms, skipped));
    }

    // the position is the top line, the others hang below it
    private static List<HologramData> convert(String id, ConfigurationSection section) {
        List<Line> lines = section.getStringList("lines").stream().map(HolographicDisplaysImporter::line).toList();
        return position(section)
                .map(top -> StackedLines.toHolograms(id, top, 0, lines, null, List.of()))
                .orElse(List.of());
    }

    private static Line line(String content) {
        if (content.toLowerCase(Locale.ROOT).startsWith(ICON_PREFIX)) return new Line(Kind.ICON, iconItem(content), ICON_HEIGHT);
        if (content.trim().equalsIgnoreCase(EMPTY_LINE)) return new Line(Kind.TEXT, "", TEXT_HEIGHT);
        String text = PAPI_PLACEHOLDER.matcher(content).replaceAll("%$1%").replace("&u", "<rainbow>");
        return new Line(Kind.TEXT, LegacyColors.toMiniMessage(text), TEXT_HEIGHT);
    }

    // icon:MATERIAL, optionally followed by :data and {nbt}
    private static String iconItem(String line) {
        String material = line.substring(ICON_PREFIX.length()).split("\\{", 2)[0].replace(" ", "").split(":", 2)[0];
        return material.toLowerCase(Locale.ROOT);
    }

    // version 3 writes a position section, version 2 a "world, x, y, z" string
    private static Optional<Position> position(ConfigurationSection section) {
        ConfigurationSection position = section.getConfigurationSection("position");
        if (position != null && position.isString("world")) {
            return Optional.of(new Position(position.getString("world"), position.getDouble("x"),
                    position.getDouble("y"), position.getDouble("z")));
        }
        String[] parts = section.getString("location", "").split(",");
        if (parts.length < 4) return Optional.empty();
        try {
            return Optional.of(new Position(parts[0].trim(), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim())));
        } catch (NumberFormatException broken) {
            return Optional.empty();
        }
    }
}
