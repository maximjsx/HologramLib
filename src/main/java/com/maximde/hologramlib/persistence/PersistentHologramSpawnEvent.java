package com.maximde.hologramlib.persistence;

import com.maximde.hologramlib.hologram.Hologram;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;

/**
 * Called right before a saved hologram spawns: on start, when its world loads and after every change.
 * You can add what can't be saved here, like click handlers or animations.
 */
@Getter
@RequiredArgsConstructor
public final class PersistentHologramSpawnEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final HologramData data;
    private final Hologram<?> hologram;

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
