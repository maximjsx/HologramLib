package com.maximde.hologramlib.command;

import lombok.Getter;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

@Getter
final class CommandFailure extends RuntimeException {

    private final String key;
    private final TagResolver[] placeholders;

    CommandFailure(String key, TagResolver... placeholders) {
        super(key, null, false, false);
        this.key = key;
        this.placeholders = placeholders;
    }
}
