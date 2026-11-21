package com.maximde.hologramlib.hologram.custom;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.hologram.Transition;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.FontWidth;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A text hologram with several pages. A click turns the page for the clicking player only, and optionally all pages
 * turn on their own every few seconds with a {@link Transition}. Only the text changes, the entity stays, so turning
 * a page never flickers.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class PagedHologram implements HologramManager.Events {

    @Getter
    private final TextHologram hologram;

    private final List<String> pages = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> turnedByClick = new ConcurrentHashMap<>();
    private final AtomicInteger turnedByTime = new AtomicInteger();
    private @Nullable TaskHandle autoTurn;

    /**
     * Turns every page on its own every this many ticks, 0 only on click. Takes effect on spawn.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile int autoTurnTicks = 0;

    @Getter @Setter @Accessors(chain = true)
    private volatile Transition transition = Transition.SLIDE;

    @Getter @Setter @Accessors(chain = true)
    private volatile int transitionTicks = 6;

    /**
     * Dots under the text showing which page is open.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile boolean pageDots = true;

    @Getter @Setter @Accessors(chain = true)
    private volatile @Nullable String clickSound = "item.book.page_turn";

    public PagedHologram(String id) {
        this.hologram = new TextHologram(id).setTextProvider(this::textFor);
        hologram.onInteract(this::turn);
    }

    /**
     * @param text MiniMessage
     */
    public PagedHologram addPage(String text) {
        pages.add(text);
        // sized for the widest page
        hologram.setMiniMessageText(widestPage() + dotsLine(0));
        return this;
    }

    public List<String> getPages() {
        return List.copyOf(pages);
    }

    public int getPage(Player player) {
        if (pages.isEmpty()) return 0;
        return Math.floorMod(turnedByTime.get() + turnedByClick.getOrDefault(player.getUniqueId(), 0), pages.size());
    }

    public PagedHologram spawn(HologramManager manager, Location location) {
        fitClickBox();
        manager.spawn(hologram, location, false);
        manager.registerEventHandler(this);
        int period = autoTurnTicks;
        if (period > 0) autoTurn = BukkitTasks.runTaskTimer(() -> turnAll(manager), period, period);
        return this;
    }

    public boolean remove(HologramManager manager) {
        if (autoTurn != null) autoTurn.cancel();
        autoTurn = null;
        manager.removeEventHandler(this);
        return manager.remove(hologram);
    }

    @Override
    public void onJoin(Player player) {}

    @Override
    public void onQuit(Player player) {
        turnedByClick.remove(player.getUniqueId());
    }

    private void turn(Player player) {
        turnedByClick.merge(player.getUniqueId(), 1, Integer::sum);
        String sound = clickSound;
        if (sound != null) player.playSound(player.getLocation(), sound, 1, 1);
        hologram.refreshTexts();
    }

    private void turnAll(HologramManager manager) {
        float scale = hologram.getScale().x;
        float width = FontWidth.of(widestPage()) * scale / FontWidth.PIXELS_PER_BLOCK;
        float height = lines(widestPage() + dotsLine(0)) * FontWidth.LINE_HEIGHT * scale / FontWidth.PIXELS_PER_BLOCK;
        manager.transition(hologram, transition, transitionTicks, width, height, () -> {
            turnedByTime.incrementAndGet();
            hologram.refreshTexts();
        });
    }

    private String textFor(Player viewer) {
        if (pages.isEmpty()) return "";
        int page = getPage(viewer);
        return pages.get(page) + dotsLine(page);
    }

    private String dotsLine(int open) {
        if (!pageDots || pages.size() < 2) return "";
        StringBuilder dots = new StringBuilder("<newline>");
        for (int page = 0; page < pages.size(); page++) {
            dots.append(page == open ? "<white>● " : "<dark_gray>● ");
        }
        return dots.toString().stripTrailing();
    }

    private String widestPage() {
        return pages.stream().max(Comparator.comparingInt(FontWidth::of)).orElse("");
    }

    private void fitClickBox() {
        String text = widestPage() + dotsLine(0);
        float scale = hologram.getScale().x;
        hologram.getInteractionBox()
                .setWidth(FontWidth.of(text) * scale / FontWidth.PIXELS_PER_BLOCK + 0.1f)
                .setHeight(lines(text) * FontWidth.LINE_HEIGHT * scale / FontWidth.PIXELS_PER_BLOCK + 0.1f);
    }

    private static int lines(String text) {
        return text.split("<newline>|<br>|\n", -1).length;
    }
}
