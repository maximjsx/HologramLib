package com.maximde.hologramlib.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class MiniMessage {
    private static final net.kyori.adventure.text.minimessage.MiniMessage minimessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
    private static final LegacyComponentSerializer legacy = LegacyComponentSerializer.builder()
            .hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    public static Component get(String message) {
        return minimessage.deserialize(message);
    }

    public static String stripTags(String message) {
        return minimessage.stripTags(message);
    }

    public static String toLegacy(String message, TagResolver... placeholders) {
        return legacy.serialize(minimessage.deserialize(message, placeholders));
    }
}
