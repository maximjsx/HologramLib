package com.maximde.hologramlib.persistence;

import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads {@code plugins/DecentHolograms/holograms/*.yml}. Only the first page is imported, the text lines become one
 * text hologram and an {@code #ICON} line an item hologram next to it. Heads and entities are left out.
 */
@RequiredArgsConstructor
public final class DecentHologramsImporter {

    public record Result(List<HologramData> holograms, int skipped) {}

    private record Line(String content, double height) {

        boolean isText() {
            return !content.startsWith("#");
        }

        boolean isIcon() {
            return content.startsWith("#ICON:");
        }
    }

    private record Position(String world, double x, double y, double z) {}

    // text displays grow up from their location and item displays sit centered on it
    private record Layout(List<String> texts, double textBottom, @Nullable String icon, double iconCenter) {

        static Layout of(List<Line> lines, double top) {
            List<String> texts = new ArrayList<>();
            double textBottom = top;
            String icon = null;
            double iconCenter = top;
            double lineTop = top;
            for (Line line : lines) {
                if (line.isText()) {
                    texts.add(toMiniMessage(line.content()));
                    textBottom = lineTop - line.height();
                } else if (line.isIcon() && icon == null) {
                    icon = iconItem(line.content());
                    iconCenter = lineTop - line.height() / 2;
                }
                lineTop -= line.height();
            }
            return new Layout(texts, textBottom, icon, iconCenter);
        }
    }

    private static final long TIMEOUT_SECONDS = 30;
    private static final float ICON_SCALE = 0.5f;
    private static final Pattern INVALID_ID_CHARS = Pattern.compile("[^A-Za-z0-9_-]");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern GRADIENT = Pattern.compile("<#([0-9a-fA-F]{6})>(.*?)</#([0-9a-fA-F]{6})>");
    private static final Pattern BRACED_HEX = Pattern.compile("\\{#([0-9a-fA-F]{6})}");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§](#[0-9a-fA-F]{6}|x(?:[&§][0-9a-fA-F]){6}|[0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"), Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    @Getter
    private final Path folder;
    private final Logger logger;

    /**
     * Empty if DecentHolograms has no folder
     */
    public CompletableFuture<Optional<Result>> read() {
        return CompletableFuture.supplyAsync(this::readAll, BukkitTasks::runTaskAsync)
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, error) -> {
                    if (error != null) logger.log(Level.SEVERE, "Importing from DecentHolograms failed", error);
                });
    }

    private Optional<Result> readAll() {
        if (!Files.isDirectory(folder)) return Optional.empty();
        List<HologramData> holograms = new ArrayList<>();
        int skipped = 0;
        for (Path file : ymlFiles()) {
            String id = INVALID_ID_CHARS.matcher(file.getFileName().toString().replaceFirst("\\.yml$", "")).replaceAll("_");
            List<HologramData> converted = convert(id, YamlConfiguration.loadConfiguration(file.toFile()));
            if (converted.isEmpty()) {
                logger.info("Skipping the DecentHologram " + id + ", it is disabled, broken or has nothing to import");
                skipped++;
            }
            holograms.addAll(converted);
        }
        return Optional.of(new Result(holograms, skipped));
    }

    private List<Path> ymlFiles() {
        try (Stream<Path> files = Files.list(folder)) {
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
        double totalHeight = lines.stream().mapToDouble(Line::height).sum();
        double top = yaml.getBoolean("down-origin", false) ? position.y() + totalHeight : position.y();
        Layout layout = Layout.of(lines, top);

        String permission = yaml.getString("permission", "");
        String viewPermission = permission.isBlank() ? null : permission;
        float yaw = (float) yaml.getDouble("facing");
        List<ClickAction> actions = actions(id, page.get("actions"));

        List<HologramData> holograms = new ArrayList<>();
        if (!layout.texts().isEmpty()) {
            String text = String.join("<newline>", layout.texts());
            holograms.add(new HologramData(id, HologramData.Type.TEXT, position.world(), position.x(), layout.textBottom(),
                    position.z(), yaw, text, 1f, Display.Billboard.CENTER, null, true, text.contains("%"),
                    viewPermission, actions));
        }
        if (layout.icon() != null) {
            String iconId = layout.texts().isEmpty() ? id : id + "_icon";
            holograms.add(new HologramData(iconId, HologramData.Type.ITEM, position.world(), position.x(), layout.iconCenter(),
                    position.z(), yaw, layout.icon(), ICON_SCALE, Display.Billboard.VERTICAL, null, true, false,
                    viewPermission, actions));
        }
        return holograms;
    }

    private static List<Line> lines(Object value) {
        if (!(value instanceof List<?> entries)) return List.of();
        List<Line> lines = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> line)) continue;
            String content = line.get("content") == null ? "" : String.valueOf(line.get("content"));
            double height = line.get("height") instanceof Number number ? number.doubleValue() : defaultHeight(content);
            lines.add(new Line(content, height));
        }
        return lines;
    }

    // the defaults from the onfig.yml
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
            // older DecentHolograms versions wrote decimal commas on servers with a german or french locale
            coordinates[i] = parts[parts.length - 3 + i].trim().replace(',', '.');
            if (!NUMBER.matcher(coordinates[i]).matches()) return Optional.empty();
        }
        String world = String.join(":", List.of(parts).subList(0, parts.length - 3));
        return Optional.of(new Position(world, Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]),
                Double.parseDouble(coordinates[2])));
    }

    private static String iconItem(String line) {
        String material = line.substring("#ICON:".length()).trim().split("[\\s({]", 2)[0];
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
        return action.type() == ClickAction.Type.MESSAGE ? new ClickAction(action.type(), toMiniMessage(action.data())) : action;
    }

    static String toMiniMessage(String text) {
        String converted = GRADIENT.matcher(text).replaceAll("<gradient:#$1:#$3>$2</gradient>");
        converted = BRACED_HEX.matcher(converted).replaceAll("&#$1");
        Matcher matcher = LEGACY_CODE.matcher(converted);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(tag(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String tag(String code) {
        if (code.startsWith("#")) return "<reset><" + code + ">";
        if (code.length() > 1) return "<reset><#" + code.replaceAll("[x&§]", "") + ">";
        char lower = Character.toLowerCase(code.charAt(0));
        String name = LEGACY_TAGS.get(lower);
        return Character.digit(lower, 16) >= 0 ? "<reset><" + name + ">" : "<" + name + ">";
    }
}
