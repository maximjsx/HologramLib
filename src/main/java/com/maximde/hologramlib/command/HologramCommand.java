package com.maximde.hologramlib.command;

import com.maximde.hologramlib.persistence.ClickAction;
import com.maximde.hologramlib.persistence.HologramData;
import com.maximde.hologramlib.persistence.PersistentHolograms;
import com.maximde.hologramlib.utils.BukkitTasks;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.maximde.hologramlib.command.Arguments.isNone;
import static com.maximde.hologramlib.command.Arguments.lower;
import static com.maximde.hologramlib.command.Arguments.lowerNames;
import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

@RequiredArgsConstructor
public final class HologramCommand implements TabExecutor {

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final List<String> BOOLEANS = List.of("true", "false");
    private static final List<String> ACTION_TYPES = Arrays.stream(ClickAction.Type.values()).map(type -> type + ":").toList();
    private static final double DEFAULT_NEAR_RADIUS = 20;

    private record Subcommand(String usage, int minArgs, boolean playersOnly, BiConsumer<CommandSender, String[]> handler) {}

    private final PersistentHolograms holograms;
    private final Messages messages;
    private final Map<String, Subcommand> subcommands = buildSubcommands();

    private Map<String, Subcommand> buildSubcommands() {
        Map<String, Subcommand> subcommands = new LinkedHashMap<>();
        subcommands.put("create", new Subcommand("create <id> <text|item|block> [content]", 2, true, this::create));
        subcommands.put("delete", new Subcommand("delete <id>", 1, false, this::delete));
        subcommands.put("list", new Subcommand("list", 0, false, this::list));
        subcommands.put("near", new Subcommand("near [radius]", 0, true, this::near));
        subcommands.put("info", new Subcommand("info <id>", 1, false, this::info));
        subcommands.put("movehere", new Subcommand("movehere <id>", 1, true, this::moveHere));
        subcommands.put("tp", new Subcommand("tp <id>", 1, true, this::teleport));
        subcommands.put("copy", new Subcommand("copy <id> <new id>", 2, true, this::copy));
        subcommands.put("text", new Subcommand("text <id> <content>", 2, false, this::text));
        subcommands.put("scale", new Subcommand("scale <id> <scale>", 2, false, (sender, args) ->
                edit(sender, args[1], data -> data.withScale(Arguments.positive(args[2])))));
        subcommands.put("billboard", new Subcommand("billboard <id> <fixed|vertical|horizontal|center>", 2, false, (sender, args) ->
                edit(sender, args[1], data -> data.withBillboard(Arguments.option(Display.Billboard.class, args[2])))));
        subcommands.put("background", new Subcommand("background <id> <#AARRGGBB|none>", 2, false, (sender, args) ->
                edit(sender, args[1], data -> data.withBackgroundColor(isNone(args[2]) ? null : Arguments.color(args[2])))));
        subcommands.put("shadow", new Subcommand("shadow <id> <true|false>", 2, false, (sender, args) ->
                edit(sender, args[1], data -> data.withShadow(Arguments.bool(args[2])))));
        subcommands.put("placeholders", new Subcommand("placeholders <id> <true|false>", 2, false, (sender, args) ->
                edit(sender, args[1], data -> data.withPlaceholders(Arguments.bool(args[2])))));
        subcommands.put("permission", new Subcommand("permission <id> <permission|none>", 2, false, (sender, args) ->
                edit(sender, args[1], data -> data.withViewPermission(isNone(args[2]) ? null : args[2]))));
        subcommands.put("action", new Subcommand("action <id> <add <TYPE:data>|clear>", 2, false, this::action));
        return subcommands;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            messages.send(sender, "help", unparsed("label", label));
            return true;
        }
        if (args.length - 1 < subcommand.minArgs()) {
            messages.send(sender, "usage", unparsed("label", label), unparsed("usage", subcommand.usage()));
            return true;
        }
        if (subcommand.playersOnly() && !(sender instanceof Player)) {
            messages.send(sender, "players-only");
            return true;
        }
        try {
            subcommand.handler().accept(sender, args);
        } catch (CommandFailure failure) {
            messages.send(sender, failure.getKey(), failure.getPlaceholders());
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        String id = freeId(args[1]);
        HologramData.Type type = Arguments.option(HologramData.Type.class, args[2]);
        String content = switch (type) {
            case TEXT -> Arguments.text(args, 3);
            case ITEM -> args.length > 3 ? args[3] : heldItem(player);
            case BLOCK -> Arguments.require(args, 3, "missing-block");
        };
        save(HologramData.create(id, type, player.getLocation(), content));
        messages.send(sender, "created", unparsed("id", id));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!holograms.delete(args[1])) throw new CommandFailure("unknown-hologram", unparsed("id", args[1]));
        messages.send(sender, "deleted", unparsed("id", args[1]));
    }

    private void list(CommandSender sender, String[] args) {
        messages.send(sender, "list-header", unparsed("count", String.valueOf(holograms.all().size())));
        holograms.all().forEach(data -> sendEntry(sender, data));
    }

    private void near(CommandSender sender, String[] args) {
        Location location = ((Player) sender).getLocation();
        double radius = args.length > 1 ? Arguments.positive(args[1]) : DEFAULT_NEAR_RADIUS;
        List<HologramData> near = holograms.all().stream()
                .filter(data -> data.world().equals(location.getWorld().getName()))
                .filter(data -> location.distanceSquared(new Location(location.getWorld(), data.x(), data.y(), data.z())) <= radius * radius)
                .toList();
        messages.send(sender, "near-header", unparsed("count", String.valueOf(near.size())), unparsed("radius", format(radius)));
        near.forEach(data -> sendEntry(sender, data));
    }

    private void info(CommandSender sender, String[] args) {
        HologramData data = require(args[1]);
        messages.send(sender, "info", unparsed("id", data.id()), unparsed("type", lower(data.type())), location(data),
                unparsed("content", data.content()),
                unparsed("scale", format(data.scale())),
                unparsed("billboard", lower(data.billboard())),
                unparsed("permission", data.viewPermission() == null ? "none" : data.viewPermission()),
                unparsed("placeholders", String.valueOf(data.placeholders())),
                unparsed("actions", data.actions().isEmpty() ? "none" : data.actions().stream().map(ClickAction::toString).collect(Collectors.joining(", "))));
    }

    private void moveHere(CommandSender sender, String[] args) {
        HologramData data = require(args[1]);
        save(data.withLocation(((Player) sender).getLocation()));
        messages.send(sender, "moved", unparsed("id", data.id()));
    }

    private void teleport(CommandSender sender, String[] args) {
        HologramData data = require(args[1]);
        World world = Bukkit.getWorld(data.world());
        if (world == null) throw new CommandFailure("world-not-loaded", unparsed("world", data.world()));
        BukkitTasks.teleport((Player) sender, new Location(world, data.x(), data.y(), data.z(), data.yaw(), 0));
        messages.send(sender, "teleported", unparsed("id", data.id()));
    }

    private void copy(CommandSender sender, String[] args) {
        HologramData data = require(args[1]);
        String copyId = freeId(args[2]);
        save(data.withId(copyId).withLocation(((Player) sender).getLocation()));
        messages.send(sender, "copied", unparsed("id", data.id()), unparsed("copy", copyId));
    }

    private void text(CommandSender sender, String[] args) {
        edit(sender, args[1], data -> data.withContent(data.type() == HologramData.Type.TEXT ? Arguments.text(args, 2) : args[2]));
    }

    private void action(CommandSender sender, String[] args) {
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "clear" -> edit(sender, args[1], data -> data.withActions(List.of()));
            case "add" -> {
                ClickAction action = Arguments.action(Arguments.rest(args, 3, "invalid-action"));
                edit(sender, args[1], data -> data.withActions(Stream.concat(data.actions().stream(), Stream.of(action)).toList()));
            }
            default -> throw new CommandFailure("unknown-option", unparsed("value", args[2]), unparsed("options", "add, clear"));
        }
    }

    private void edit(CommandSender sender, String id, UnaryOperator<HologramData> change) {
        save(change.apply(require(id)));
        messages.send(sender, "updated", unparsed("id", id));
    }

    private void save(HologramData data) {
        if (!holograms.put(data)) throw new CommandFailure("unknown-content", unparsed("content", data.content()));
    }

    private HologramData require(String id) {
        return holograms.get(id).orElseThrow(() -> new CommandFailure("unknown-hologram", unparsed("id", id)));
    }

    private String freeId(String id) {
        if (!VALID_ID.matcher(id).matches()) throw new CommandFailure("invalid-id");
        if (holograms.exists(id)) throw new CommandFailure("id-taken", unparsed("id", id));
        return id;
    }

    private void sendEntry(CommandSender sender, HologramData data) {
        messages.send(sender, "list-entry", unparsed("id", data.id()), unparsed("type", lower(data.type())), location(data));
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, String @NonNull [] args) {
        return StringUtil.copyPartialMatches(args[args.length - 1], options(args), new ArrayList<>());
    }

    private List<String> options(String[] args) {
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1) return Stream.concat(Stream.of("help"), subcommands.keySet().stream()).toList();
        if (args.length == 2) {
            return switch (subcommand) {
                case "help", "list", "near", "create" -> List.of();
                default -> holograms.all().stream().map(HologramData::id).toList();
            };
        }
        if (args.length == 3) {
            return switch (subcommand) {
                case "create" -> lowerNames(HologramData.Type.values());
                case "billboard" -> lowerNames(Display.Billboard.values());
                case "shadow", "placeholders" -> BOOLEANS;
                case "background" -> List.of("none", "#40000000");
                case "permission" -> List.of("none");
                case "action" -> List.of("add", "clear");
                default -> List.of();
            };
        }
        if (args.length == 4 && subcommand.equals("create")) {
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "item" -> materials(Material::isItem, args[3]);
                case "block" -> materials(Material::isBlock, args[3]);
                default -> List.of();
            };
        }
        if (args.length == 4 && subcommand.equals("action") && args[2].equalsIgnoreCase("add")) return ACTION_TYPES;
        return List.of();
    }

    private static List<String> materials(Predicate<Material> filter, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy() && filter.test(material))
                .map(material -> material.getKey().getKey())
                .filter(name -> name.startsWith(prefix))
                .toList();
    }

    private static String heldItem(Player player) {
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held.isAir()) throw new CommandFailure("missing-item");
        return held.getKey().toString();
    }

    private static TagResolver location(HologramData data) {
        return TagResolver.resolver(unparsed("world", data.world()),
                unparsed("x", format(data.x())), unparsed("y", format(data.y())), unparsed("z", format(data.z())));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
