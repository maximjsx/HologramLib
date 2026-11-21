package com.maximde.hologramlib.persistence.importer;

import com.maximde.hologramlib.persistence.ClickAction;
import com.maximde.hologramlib.persistence.HologramData;
import com.maximde.hologramlib.persistence.importer.StackedLines.Kind;
import com.maximde.hologramlib.persistence.importer.StackedLines.Line;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads {@code plugins/DecentHolograms/holograms/*.yml}. Only the first page is imported
 */
public final class DecentHologramsImporter extends HologramImporter {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String ICON_PREFIX = "#ICON:";

    public DecentHologramsImporter(Path pluginsFolder, Logger logger) {
        super("DecentHolograms", pluginsFolder.resolve("DecentHolograms").resolve("holograms"), logger);
    }

    @Override
    protected Optional<Result> readAll() {
        if (!Files.isDirectory(getSource())) return Optional.empty();
        List<HologramData> holograms = new ArrayList<>();
        int skipped = 0;
        for (Path file : ymlFiles()) {
            String id = validId(file.getFileName().toString().replaceFirst("\\.yml$", ""));
            List<HologramData> converted = convert(id, YamlConfiguration.loadConfiguration(file.toFile()));
            if (converted.isEmpty()) {
                logSkipped(id);
                skipped++;
            }
            holograms.addAll(converted);
        }
        return Optional.of(new Result(holograms, skipped));
    }

    private List<Path> ymlFiles() {
        try (Stream<Path> files = Files.list(getSource())) {
            return files.filter(file -> file.getFileName().toString().endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<HologramData> convert(String id, YamlConfiguration yaml) {
        if (!yaml.getBoolean("enabled", true)) return List.of();
        List<Map<?, ?>> pages = yaml.getMapList("pages");
        if (pages.isEmpty()) return List.of();
        return position(yaml.getString("location", ""))
                .map(position -> convert(id, yaml, pages.get(0), position))
                .orElse(List.of());
    }

    private List<HologramData> convert(String id, YamlConfiguration yaml, Map<?, ?> page, Position position) {
        List<Line> lines = lines(page.get("lines"));
        Position top = yaml.getBoolean("down-origin", false) ? position.up(StackedLines.height(lines)) : position;
        String permission = yaml.getString("permission", "");
        return StackedLines.toHolograms(id, top, (float) yaml.getDouble("facing"), lines,
                permission.isBlank() ? null : permission, actions(id, page.get("actions")));
    }

    private static List<Line> lines(Object value) {
        if (!(value instanceof List<?> entries)) return List.of();
        List<Line> lines = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> line)) continue;
            String content = line.get("content") == null ? "" : String.valueOf(line.get("content"));
            double height = line.get("height") instanceof Number number ? number.doubleValue() : defaultHeight(content);
            lines.add(line(content, height));
        }
        return lines;
    }

    private static Line line(String content, double height) {
        if (!content.startsWith("#")) return new Line(Kind.TEXT, LegacyColors.toMiniMessage(content), height);
        if (content.startsWith(ICON_PREFIX)) return new Line(Kind.ICON, iconItem(content), height);
        return new Line(Kind.OTHER, content, height);
    }

    // the defaults from the config.yml
    private static double defaultHeight(String content) {
        String type = content.startsWith("#") ? content.substring(0, Math.max(content.indexOf(':'), 1)) : "";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "" -> 0.3;
            case "#HEAD" -> 0.75;
            default -> 0.6;
        };
    }

    // world:x:y:z, the world name can contain colons too
    private static Optional<Position> position(String location) {
        String[] parts = location.split(":");
        if (parts.length < 4) return Optional.empty();
        String[] coordinates = new String[3];
        for (int i = 0; i < 3; i++) {
            // for older versions
            coordinates[i] = parts[parts.length - 3 + i].trim().replace(',', '.');
            if (!NUMBER.matcher(coordinates[i]).matches()) return Optional.empty();
        }
        String world = String.join(":", List.of(parts).subList(0, parts.length - 3));
        return Optional.of(new Position(world, Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]),
                Double.parseDouble(coordinates[2])));
    }

    private static String iconItem(String line) {
        String material = line.substring(ICON_PREFIX.length()).trim().split("[\\s({]", 2)[0];
        return material.toLowerCase(Locale.ROOT);
    }

    // DecentHolograms runs actions per click type, HologramLib on any click so they get merged
    private List<ClickAction> actions(String id, Object value) {
        if (!(value instanceof Map<?, ?> byClickType)) return List.of();
        List<ClickAction> actions = new ArrayList<>();
        for (Object clickActions : byClickType.values()) {
            if (!(clickActions instanceof List<?> written)) continue;
            for (Object action : written) {
                Optional<ClickAction> converted = ClickAction.parse(String.valueOf(action)).map(DecentHologramsImporter::colored);
                if (converted.isEmpty()) logger.info("Leaving out the action " + action + " of the DecentHologram " + id);
                converted.filter(parsed -> !actions.contains(parsed)).ifPresent(actions::add);
            }
        }
        return actions;
    }

    private static ClickAction colored(ClickAction action) {
        if (action.type() != ClickAction.Type.MESSAGE) return action;
        return new ClickAction(action.type(), LegacyColors.toMiniMessage(action.data()));
    }
}
