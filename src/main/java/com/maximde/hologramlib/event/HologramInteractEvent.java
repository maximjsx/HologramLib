package com.maximde.hologramlib.event;

import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.InteractionBox;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A player clicked an interaction box, before its {@code onInteract} callback runs. Cancelling it skips the callback.
 * Called on the thread that owns the player, which on Folia is the player's region thread.
 */
@Getter
@RequiredArgsConstructor
public final class HologramInteractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final InteractionBox interactionBox;
    @Getter(lombok.AccessLevel.NONE)
    private final @Nullable Hologram<?> hologram;
    /** True for a left click, false for a right click. */
    private final boolean leftClick;
    @Setter
    private boolean cancelled;

    /**
     * The hologram the box belongs to, empty for an interaction box spawned on its own.
     */
    public Optional<Hologram<?>> getHologram() {
        return Optional.ofNullable(hologram);
    }

    @Override
    public @NonNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
