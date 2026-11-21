package com.maximde.hologramlib.persistence;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArgbColor {

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");

    private ArgbColor() {}

    public static String format(int argb) {
        return String.format("#%08X", argb);
    }

    public static Optional<Integer> parse(String hex) {
        Matcher matcher = HEX.matcher(hex);
        if (!matcher.matches()) return Optional.empty();
        String digits = matcher.group(1);
        long value = Long.parseLong(digits, 16);
        return Optional.of((int) (digits.length() == 6 ? 0xFF000000L | value : value));
    }
}
