package com.maximde.hologramlib.command;

import com.maximde.hologramlib.persistence.ArgbColor;
import com.maximde.hologramlib.persistence.ClickAction;
import com.maximde.hologramlib.persistence.HologramData;
import com.maximde.hologramlib.utils.Enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed;

final class Arguments {

    private Arguments() {}

    static String require(String[] args, int index, String missingKey) {
        if (args.length <= index) throw new CommandFailure(missingKey);
        return args[index];
    }

    static String rest(String[] args, int from, String missingKey) {
        require(args, from, missingKey);
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    static String text(String[] args, int from) {
        return rest(args, from, "missing-text").replace("\\n", "<newline>");
    }

    static HologramData.Scale scale(String[] args, int from) {
        int given = args.length - from;
        if (given == 1) return HologramData.Scale.uniform(positive(args[from]));
        if (given != 3) throw new CommandFailure("invalid-scale");
        return new HologramData.Scale(positive(args[from]), positive(args[from + 1]), positive(args[from + 2]));
    }

    static float positive(String value) {
        return parseFloat(value)
                .filter(number -> number > 0 && Float.isFinite(number))
                .orElseThrow(() -> new CommandFailure("not-positive", unparsed("value", value)));
    }

    static boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new CommandFailure("not-boolean", unparsed("value", value));
        };
    }

    static <E extends Enum<E>> E option(Class<E> type, String value) {
        return Enums.parse(type, value).orElseThrow(() -> new CommandFailure("unknown-option",
                unparsed("value", value), unparsed("options", String.join(", ", lowerNames(type.getEnumConstants())))));
    }

    static int color(String value) {
        return ArgbColor.parse(value).orElseThrow(() -> new CommandFailure("invalid-color", unparsed("value", value)));
    }

    static ClickAction action(String value) {
        return ClickAction.parse(value).orElseThrow(() -> new CommandFailure("invalid-action"));
    }

    static boolean isNone(String value) {
        return value.equalsIgnoreCase("none");
    }

    static List<String> lowerNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Arguments::lower).toList();
    }

    static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static Optional<Float> parseFloat(String value) {
        try {
            return Optional.of(Float.parseFloat(value));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
