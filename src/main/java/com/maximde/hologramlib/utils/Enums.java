package com.maximde.hologramlib.utils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class Enums {

    private Enums() {}

    public static <E extends Enum<E>> Optional<E> parse(Class<E> type, String name) {
        String upperName = name.toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(upperName))
                .findFirst();
    }
}
