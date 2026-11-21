package com.maximde.hologramlib.persistence;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.Optional;

final class HologramContent {

    private HologramContent() {}

    static boolean isValid(HologramData data) {
        return switch (data.type()) {
            case TEXT -> true;
            case ITEM -> item(data.content()).isPresent();
            case BLOCK -> block(data.content()).isPresent();
        };
    }

    static Optional<ItemStack> item(String key) {
        return Optional.ofNullable(ItemTypes.getByName(namespaced(key)))
                .map(type -> new ItemStack.Builder().type(type).build());
    }

    static Optional<Integer> block(String state) {
        try {
            // packetevents makes air out of something like oak_stairs[facing=east], so bukkit fills in the rest first
            String complete = Bukkit.createBlockData(namespaced(state)).getAsString();
            return Optional.of(WrappedBlockState.getByString(complete).getGlobalId());
        } catch (IllegalArgumentException unknownBlock) {
            return Optional.empty();
        }
    }

    private static String namespaced(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains(":") ? lower : "minecraft:" + lower;
    }
}
