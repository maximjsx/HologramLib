package com.maximde.hologramlib.hologram.custom;

import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.hologram.Transition;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.FontWidth;
import com.maximde.hologramlib.utils.PlayerHeadComponent;
import com.maximde.hologramlib.utils.TaskHandle;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A leaderboard in a single text display. The gap between each name and its score is filled with dots up to
 * {@link #setWidth a fixed pixel width}, so the scores line up on the right like in a table. Optional
 * {@link Toggle toggles} under it switch what each player sees, for that player only.
 * <p>
 * Spawn it with {@link HologramManager#spawn(TextLeaderboard, Location)}. The board faces the yaw of that location.
 * The title, entries and footer are worked out per viewer every {@link #setUpdatePeriod update period} and when a
 * toggle is clicked, also off the main thread, so keep them fast and thread safe.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class TextLeaderboard implements HologramManager.Events {

    public static final String DEFAULT_ROW_FORMAT = "<white>{place}. {head} {extra}<white>{name} {fill} <white>{score}";
    public static final String DEFAULT_OWN_ROW_FORMAT = "<green>{place}. {head} {extra}<green>{name} {fill} <green>{score}";
    public static final String DEFAULT_TOGGLE_LABEL = "<yellow>{state}<newline><white>Click to <yellow>Toggle";

    private static final String LINE_BREAK = "<reset><newline>";
    private static final String COLUMN = "{column}";
    private static final String FILL = "{fill}";

    /**
     * @param extra MiniMessage put in front of the name, like rank icons
     */
    public record Entry(UUID uuid, String name, Number score, String extra) {
        public Entry(UUID uuid, String name, Number score) {
            this(uuid, name, score, "");
        }
    }

    /**
     * What one viewer looks at: who they are, the state each of their toggles is in and the board's current
     * {@link #setCycle cycle}, counting up from 0.
     */
    public record View(Player viewer, Map<String, String> states, int cycle) {
        public String state(String toggleId) {
            return states.getOrDefault(toggleId, "");
        }
    }

    /**
     * A clickable label under the board. Each click moves the clicking player to the next state.
     *
     * @param label  MiniMessage, {@code {state}} is replaced with the viewer's current state
     * @param offset where the label sits relative to the bottom middle of the board, in blocks, x to the right as
     *               the board is read. {@code null} puts it into the row with the other toggles without an offset
     */
    public record Toggle(String id, List<String> states, String label, @Nullable Vector2f offset) {

        public Toggle {
            if (id.contains(" ")) throw new IllegalArgumentException("A toggle id cannot contain spaces");
            if (states.isEmpty()) throw new IllegalArgumentException("A toggle needs at least one state");
            states = List.copyOf(states);
        }

        public static Toggle of(String id, String... states) {
            return new Toggle(id, List.of(states), DEFAULT_TOGGLE_LABEL, null);
        }

        public Toggle withLabel(String label) {
            return new Toggle(id, states, label, offset);
        }

        public Toggle at(float x, float y) {
            return new Toggle(id, states, label, new Vector2f(x, y));
        }
    }

    @Getter
    private final String id;

    /**
     * The text display holding the whole board, for its scale, background, billboard and so on.
     */
    @Getter
    private final TextHologram board;

    private final List<Toggle> toggles = new CopyOnWriteArrayList<>();
    private final Map<UUID, Map<String, Integer>> states = new ConcurrentHashMap<>();
    private volatile List<TextHologram> toggleHolograms = List.of();

    private volatile Function<View, String> title = view -> "";
    private volatile Function<View, List<Entry>> entries = view -> List.of();
    private volatile Function<View, String> footer = view -> "";

    /**
     * Pixels from the left edge of a row to the right edge of its score.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile int width = 200;

    /**
     * MiniMessage with {@code {place}}, {@code {place_color}}, {@code {head}}, {@code {extra}}, {@code {name}},
     * {@code {score}} and {@code {fill}}, where the dots go. Keep {@code {fill}} out of bold text, bold dots are wider. Every row is
     * padded so the head, or the name without a head, starts at the same pixel. {@code {column}} moves that point.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile String rowFormat = DEFAULT_ROW_FORMAT;

    /**
     * Like {@link #getRowFormat()} for the viewer's own row, {@code null} shows it like every other row.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile @Nullable String ownRowFormat = DEFAULT_OWN_ROW_FORMAT;

    @Getter @Setter @Accessors(chain = true)
    private volatile String fillColor = "#555555";

    @Getter @Setter @Accessors(chain = true)
    private volatile int maxRows = 10;

    @Getter @Setter @Accessors(chain = true)
    private volatile boolean ascending = false;

    @Getter @Setter @Accessors(chain = true)
    private volatile Function<Number, String> scoreFormatter = TextLeaderboard::formatScore;

    /**
     * Shows the rows around the viewer's own place instead of the top ones while this is true for them.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile Predicate<View> aroundViewer = view -> false;

    /**
     * Blocks between the middles of neighbouring toggles. 0 spreads them over the board's width, but never so
     * close that their labels touch.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile float toggleSpacing = 0;

    /**
     * How far below the board the row of toggles starts, in blocks.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile float toggleDrop = 0.75f;

    @Getter @Setter @Accessors(chain = true)
    private volatile float toggleScale = 1f;

    @Getter @Setter @Accessors(chain = true)
    private volatile int toggleBackground = 0;

    /**
     * Played to a player who clicks a toggle, {@code null} for none.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile @Nullable String clickSound = "ui.button.click";

    /**
     * A panel behind the board. Its progress bar, if it has one, runs empty until the next {@link #setCycle cycle}.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile @Nullable Backdrop backdrop;

    /**
     * How the board changes every {@link #setCycle cycle}.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile Transition transition = Transition.FLIP;

    @Getter @Setter @Accessors(chain = true)
    private volatile int transitionTicks = 6;

    /**
     * After spawning and every cycle the rows appear one after another, this many ticks apart. 0 shows them at once.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile int rowRevealTicks = 2;

    /**
     * Puts every line into a text display of its own, so on every {@link #setCycle cycle} the lines leave and come
     * back one after another in the {@link #setRowOrder row order}, {@link #setRowRevealTicks row reveal ticks} apart,
     * instead of the whole board at once. Takes effect on spawn.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile boolean splitRows = false;

    /**
     * The order the lines of a {@link #setSplitRows split} board change in.
     */
    public enum RowOrder {
        TOP_DOWN,
        BOTTOM_UP,
        /**
         * From the middle line outwards to the title and the footer.
         */
        CENTER_OUT,
        /**
         * A new shuffle every cycle.
         */
        RANDOM
    }

    @Getter @Setter @Accessors(chain = true)
    private volatile RowOrder rowOrder = RowOrder.TOP_DOWN;

    /**
     * What {@code {place_color}} turns into for the first places, like gold, silver and bronze. Later places get
     * {@link #getOtherPlaceColor()}.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile List<String> placeColors = List.of();

    @Getter @Setter @Accessors(chain = true)
    private volatile String otherPlaceColor = "white";

    /**
     * What {@code {name}} turns into, MiniMessage. To show online players in another color, for example.
     */
    @Getter @Setter @Accessors(chain = true)
    private volatile Function<Entry, String> nameFormat = entry -> MiniMessage.miniMessage().escapeTags(entry.name());

    // split rows: the lines below the title and the cycle each one shows
    private volatile List<TextHologram> lineHolograms = List.of();
    private volatile @Nullable AtomicIntegerArray lineCycles;

    private volatile int cycleTicks;
    private final AtomicInteger cycle = new AtomicInteger();
    private volatile long revealStart;
    private final List<TaskHandle> tasks = new CopyOnWriteArrayList<>();

    public TextLeaderboard(String id) {
        this.id = id;
        this.board = new TextHologram(id)
                .setBillboard(Display.Billboard.FIXED)
                .setMaxLineWidth(Integer.MAX_VALUE)
                .setTextProvider(viewer -> lineCycles == null ? render(view(viewer)) : line(viewer, 0));
    }

    public TextLeaderboard setTitle(String title) {
        return setTitle(view -> title);
    }

    public TextLeaderboard setTitle(Function<View, String> title) {
        this.title = title;
        return this;
    }

    public TextLeaderboard setFooter(String footer) {
        return setFooter(view -> footer);
    }

    public TextLeaderboard setFooter(Function<View, String> footer) {
        this.footer = footer;
        return this;
    }

    public TextLeaderboard setEntries(List<Entry> entries) {
        List<Entry> copy = List.copyOf(entries);
        return setEntries(view -> copy);
    }

    /**
     * The entries in any order, the board sorts them. Equal scores share a place.
     */
    public TextLeaderboard setEntries(Function<View, List<Entry>> entries) {
        this.entries = entries;
        return this;
    }

    /**
     * Only toggles added before spawning show up.
     */
    public TextLeaderboard addToggle(Toggle toggle) {
        toggles.add(toggle);
        return this;
    }

    /**
     * Moves the board on to its next cycle every {@code ticks}, with the {@link #setTransition transition}. The title,
     * entries and footer read the current one from {@link View#cycle()}, to rotate through several boards for example.
     * 0 turns it off. Takes effect on spawn.
     */
    public TextLeaderboard setCycle(int ticks) {
        this.cycleTicks = ticks;
        return this;
    }

    public TextLeaderboard setUpdatePeriod(long ticks) {
        board.setUpdateTaskPeriod(ticks);
        lineHolograms.forEach(hologram -> hologram.setUpdateTaskPeriod(ticks));
        toggleHolograms.forEach(hologram -> hologram.setUpdateTaskPeriod(ticks));
        return this;
    }

    /**
     * Shows changed entries, title or footer right away instead of at the next update period.
     */
    public void refresh() {
        board.refreshTexts();
        lineHolograms.forEach(TextHologram::refreshTexts);
        toggleHolograms.forEach(TextHologram::refreshTexts);
    }

    public String getState(Player player, String toggleId) {
        return view(player).state(toggleId);
    }

    public List<TextHologram> getHolograms() {
        List<TextHologram> holograms = new ArrayList<>(lineHolograms.size() + toggleHolograms.size() + 1);
        holograms.add(board);
        holograms.addAll(lineHolograms);
        holograms.addAll(toggleHolograms);
        return holograms;
    }

    /**
     * Same as {@link HologramManager#spawn(TextLeaderboard, Location)}.
     */
    public TextLeaderboard spawn(HologramManager manager, Location location) {
        Location facing = location.clone();
        facing.setPitch(0);
        revealStart = System.currentTimeMillis();
        if (splitRows) {
            spawnLines(manager, facing);
        } else {
            manager.spawn(board, facing, false);
        }
        Backdrop panel = backdrop;
        if (panel != null) panel.spawn(manager, facing, boardWidth(), boardHeight());

        List<TextHologram> created = new ArrayList<>();
        List<Toggle> inRow = toggles.stream().filter(toggle -> toggle.offset() == null).toList();
        float spacing = toggleSpacing > 0 ? toggleSpacing : autoToggleSpacing(inRow);
        for (Toggle toggle : toggles) {
            int index = inRow.indexOf(toggle);
            Vector2f offset = toggle.offset() != null ? toggle.offset()
                    : new Vector2f((index - (inRow.size() - 1) / 2f) * spacing, -toggleDrop);
            TextHologram hologram = createToggle(toggle);
            created.add(hologram);
            manager.spawn(hologram, offset(facing, offset), false);
        }
        toggleHolograms = List.copyOf(created);
        manager.registerEventHandler(this);
        startReveal();
        startCycle(manager, panel);
        return this;
    }

    /**
     * Same as {@link HologramManager#remove(TextLeaderboard)}.
     */
    public boolean remove(HologramManager manager) {
        tasks.forEach(TaskHandle::cancel);
        tasks.clear();
        manager.removeEventHandler(this);
        Backdrop panel = backdrop;
        if (panel != null) panel.remove(manager);
        boolean removed = true;
        for (TextHologram hologram : getHolograms()) {
            removed &= manager.remove(hologram);
        }
        lineCycles = null;
        lineHolograms = List.of();
        return removed;
    }

    // title stays in the board, rows and footer get their own displays
    private void spawnLines(HologramManager manager, Location facing) {
        int lines = maxRows + 2;
        List<TextHologram> created = new ArrayList<>(lines - 1);
        for (int slot = 1; slot < lines; slot++) {
            int line = slot;
            created.add(board.copy(id + "_line_" + slot).setTextProvider(viewer -> line(viewer, line)));
        }
        lineCycles = new AtomicIntegerArray(lines);
        lineHolograms = List.copyOf(created);
        float lineHeight = lineHeight();
        manager.spawn(board, facing.clone().add(0, (lines - 1) * lineHeight, 0), false);
        for (int slot = 1; slot < lines; slot++) {
            manager.spawn(created.get(slot - 1), facing.clone().add(0, (lines - 1 - slot) * lineHeight, 0), false);
        }
    }

    private void startCycle(HologramManager manager, @Nullable Backdrop panel) {
        int period = cycleTicks;
        if (period <= 0) return;
        int ticks = transitionTicks;
        int barTicks = Math.max(1, period - ticks - 2);
        if (panel != null) panel.runProgressBar(manager, period);
        AtomicIntegerArray cycles = lineCycles;
        Runnable next = cycles == null
                ? () -> turnBoard(manager, panel, ticks, barTicks)
                : () -> turnLines(manager, panel, ticks, barTicks, cycles);
        tasks.add(BukkitTasks.runTaskTimer(next, period, period));
    }

    private void turnBoard(HologramManager manager, @Nullable Backdrop panel, int ticks, int barTicks) {
        manager.transition(board, transition, ticks, boardWidth(), boardHeight(), () -> {
            cycle.incrementAndGet();
            startReveal();
            if (panel != null) panel.runProgressBar(manager, barTicks);
        });
    }

    // lines switch cycle while hidden
    private void turnLines(HologramManager manager, @Nullable Backdrop panel, int ticks, int barTicks,
                           AtomicIntegerArray cycles) {
        cycle.incrementAndGet();
        List<TextHologram> lines = new ArrayList<>(lineHolograms.size() + 1);
        lines.add(board);
        lines.addAll(lineHolograms);
        int[] turns = turnOrder(lines.size());
        for (int slot = 0; slot < lines.size(); slot++) {
            int line = slot;
            TextHologram hologram = lines.get(slot);
            BukkitTasks.runTaskLater(() -> {
                // removed meanwhile
                if (lineCycles != cycles) return;
                manager.transition(hologram, transition, ticks, boardWidth(), lineHeight(), () -> {
                    cycles.incrementAndGet(line);
                    hologram.refreshTexts();
                    if (line == 0 && panel != null) panel.runProgressBar(manager, barTicks);
                });
            }, 1L + (long) turns[slot] * rowRevealTicks);
        }
    }

    private int[] turnOrder(int lines) {
        int[] turns = new int[lines];
        float middle = (lines - 1) / 2f;
        List<Integer> shuffled = new ArrayList<>();
        for (int line = 0; line < lines; line++) shuffled.add(line);
        Collections.shuffle(shuffled);
        for (int line = 0; line < lines; line++) {
            turns[line] = switch (rowOrder) {
                case TOP_DOWN -> line;
                case BOTTOM_UP -> lines - 1 - line;
                case CENTER_OUT -> Math.round(Math.abs(line - middle));
                case RANDOM -> shuffled.get(line);
            };
        }
        return turns;
    }

    private void startReveal() {
        revealStart = System.currentTimeMillis();
        refresh();
        int perRow = rowRevealTicks;
        if (perRow <= 0) return;
        AtomicInteger ticksLeft = new AtomicInteger(maxRows * perRow + 1);
        AtomicReference<TaskHandle> task = new AtomicReference<>();
        task.set(BukkitTasks.runTaskTimer(() -> {
            refresh();
            if (ticksLeft.decrementAndGet() > 0) return;
            Optional.ofNullable(task.get()).ifPresent(TaskHandle::cancel);
            tasks.remove(task.get());
        }, 1L, 1L));
        tasks.add(task.get());
    }

    private int revealedRows() {
        int perRow = rowRevealTicks;
        if (perRow <= 0) return Integer.MAX_VALUE;
        long ticks = (System.currentTimeMillis() - revealStart) / 50;
        return (int) (ticks / perRow) + 1;
    }

    private float boardWidth() {
        return width * board.getScale().x / FontWidth.PIXELS_PER_BLOCK;
    }

    private float boardHeight() {
        return (maxRows + 2) * lineHeight();
    }

    private float lineHeight() {
        return FontWidth.LINE_HEIGHT * board.getScale().y / FontWidth.PIXELS_PER_BLOCK;
    }

    @Override
    public void onJoin(Player player) {}

    @Override
    public void onQuit(Player player) {
        states.remove(player.getUniqueId());
    }

    /**
     * The whole board as {@code view} sees it, in MiniMessage.
     */
    public String render(View view) {
        List<String> lines = new ArrayList<>();
        String titleText = title.apply(view);
        if (!titleText.isEmpty()) lines.add(titleText);

        List<String> rows = rows(view);
        // unrevealed rows stay empty so the height doesn't jump
        int revealed = revealedRows();
        for (int index = 0; index < rows.size(); index++) {
            lines.add(index < revealed ? rows.get(index) : "");
        }

        String footerText = footer.apply(view);
        if (!footerText.isEmpty()) lines.add(footerText);
        return String.join(LINE_BREAK, lines);
    }

    // split board line in its current cycle, 0 is the title, then rows, then footer
    private String line(Player viewer, int line) {
        AtomicIntegerArray cycles = lineCycles;
        View view = view(viewer, cycles == null ? cycle.get() : cycles.get(line));
        if (line == 0) return title.apply(view);
        if (line > maxRows) return footer.apply(view);
        List<String> rows = rows(view);
        return line <= Math.min(rows.size(), revealedRows()) ? rows.get(line - 1) : "";
    }

    private List<String> rows(View view) {
        List<Entry> sorted = new ArrayList<>(entries.apply(view));
        Comparator<Entry> byScore = Comparator.comparingDouble(entry -> entry.score().doubleValue());
        sorted.sort(ascending ? byScore : byScore.reversed());
        int[] places = places(sorted);
        int from = firstShownRow(sorted, view);
        List<String> rows = new ArrayList<>();
        for (int index = from; index < Math.min(sorted.size(), from + maxRows); index++) {
            rows.add(row(places[index], sorted.get(index), view.viewer()));
        }
        return alignColumns(rows);
    }

    // pad rows so heads and names line up, also after "10."
    private List<String> alignColumns(List<String> rows) {
        int[] prefixWidths = rows.stream().mapToInt(row -> measure(row.substring(0, row.indexOf(COLUMN)))).toArray();
        int widest = Arrays.stream(prefixWidths).max().orElse(0);
        List<String> aligned = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            String row = rows.get(index).replace(COLUMN, pad(widest - prefixWidths[index]));
            aligned.add(row.replace(FILL, fill(width - measure(row.replace(FILL, "")))));
        }
        return aligned;
    }

    // ties share a place, like 1, 2, 2, 4
    private static int[] places(List<Entry> sorted) {
        int[] places = new int[sorted.size()];
        for (int index = 0; index < sorted.size(); index++) {
            boolean tied = index > 0 && sorted.get(index).score().doubleValue() == sorted.get(index - 1).score().doubleValue();
            places[index] = tied ? places[index - 1] : index + 1;
        }
        return places;
    }

    private int firstShownRow(List<Entry> sorted, View view) {
        if (!aroundViewer.test(view)) return 0;
        UUID viewer = view.viewer().getUniqueId();
        for (int index = 0; index < sorted.size(); index++) {
            if (!sorted.get(index).uuid().equals(viewer)) continue;
            return Math.max(0, Math.min(index - maxRows / 2, sorted.size() - maxRows));
        }
        return 0;
    }

    /**
     * The row with its placeholders filled in, except for the dots, and a marker where its column starts: in front of
     * the head, or of the name without a head.
     */
    private String row(int place, Entry entry, Player viewer) {
        String format = ownRowFormat != null && entry.uuid().equals(viewer.getUniqueId()) ? ownRowFormat : rowFormat;
        String anchor = format.contains("{head}") ? "{head}" : "{name}";
        String marked = format.contains(COLUMN) ? format : format.replaceFirst(Pattern.quote(anchor), COLUMN + anchor);
        List<String> colors = placeColors;
        String placeColor = place <= colors.size() ? colors.get(place - 1) : otherPlaceColor;
        return marked.replace("{place}", String.valueOf(place))
                .replace("{place_color}", "<color:" + placeColor + ">")
                .replace("{head}", PlayerHeadComponent.fromPlayer(entry.name()))
                .replace("{extra}", entry.extra())
                .replace("{name}", nameFormat.apply(entry))
                .replace("{score}", scoreFormatter.apply(entry.score()));
    }

    private int measure(String miniMessage) {
        return FontWidth.of(board.replaceFontImages(miniMessage));
    }

    // dot is 2px, bold dot 3px, together they fill any gap from 2px
    private String fill(int gap) {
        if (gap < 2) return "";
        int boldDots = gap % 2;
        int dots = (gap - 3 * boldDots) / 2;
        return "<color:" + fillColor + ">" + ".".repeat(dots) + (boldDots == 1 ? "<b>.</b>" : "") + "</color>";
    }

    // space is 4px, bold space 5px, covers every width from 12 up
    private static String pad(int pixels) {
        int bestBold = 0;
        int bestPlain = 0;
        for (int bold = 0; bold * 5 <= pixels; bold++) {
            int plain = (pixels - bold * 5) / 4;
            if (bold * 5 + plain * 4 > bestBold * 5 + bestPlain * 4) {
                bestBold = bold;
                bestPlain = plain;
            }
        }
        return " ".repeat(bestPlain) + (bestBold > 0 ? "<b>" + " ".repeat(bestBold) + "</b>" : "");
    }

    private View view(Player viewer) {
        return view(viewer, cycle.get());
    }

    private View view(Player viewer, int cycle) {
        Map<String, Integer> own = states.getOrDefault(viewer.getUniqueId(), Map.of());
        Map<String, String> current = new LinkedHashMap<>();
        for (Toggle toggle : toggles) {
            current.put(toggle.id(), toggle.states().get(own.getOrDefault(toggle.id(), 0)));
        }
        return new View(viewer, current, cycle);
    }

    private float autoToggleSpacing(List<Toggle> row) {
        int widestLabel = row.stream().mapToInt(toggle -> FontWidth.of(widestLabel(toggle))).max().orElse(0);
        float labelWidth = widestLabel * toggleScale / FontWidth.PIXELS_PER_BLOCK;
        float boardWidth = width * board.getScale().x / FontWidth.PIXELS_PER_BLOCK;
        return Math.max(labelWidth + 0.25f, boardWidth / Math.max(1, row.size()));
    }

    private static String widestLabel(Toggle toggle) {
        return toggle.states().stream()
                .map(state -> toggle.label().replace("{state}", state))
                .max(Comparator.comparingInt(FontWidth::of))
                .orElseThrow();
    }

    private TextHologram createToggle(Toggle toggle) {
        String widest = widestLabel(toggle);
        TextHologram hologram = new TextHologram(id + "_toggle_" + toggle.id())
                .setMiniMessageText(widest)
                .setBillboard(Display.Billboard.FIXED)
                .setBackgroundColor(toggleBackground)
                .setScale(toggleScale, toggleScale, toggleScale)
                .setUpdateTaskPeriod(board.getUpdateTaskPeriod())
                .setTextProvider(viewer -> toggle.label().replace("{state}", view(viewer).state(toggle.id())))
                .onInteract(player -> click(player, toggle));
        // measure the label, the char count guess is off
        int lines = widest.split("<newline>|<br>|\n", -1).length;
        hologram.getInteractionBox()
                .setWidth(FontWidth.of(widest) * toggleScale / FontWidth.PIXELS_PER_BLOCK + 0.1f)
                .setHeight(lines * FontWidth.LINE_HEIGHT * toggleScale / FontWidth.PIXELS_PER_BLOCK + 0.1f);
        return hologram;
    }

    private void click(Player player, Toggle toggle) {
        states.computeIfAbsent(player.getUniqueId(), uuid -> new ConcurrentHashMap<>())
                .merge(toggle.id(), 1, (state, step) -> (state + step) % toggle.states().size());
        String sound = clickSound;
        if (sound != null) player.playSound(player.getLocation(), sound, 1, 1);
        refresh();
    }

    // x goes to the right of whoever reads the board
    private static Location offset(Location location, Vector2f offset) {
        double yaw = Math.toRadians(location.getYaw());
        return location.clone().add(Math.cos(yaw) * offset.x, offset.y, Math.sin(yaw) * offset.x);
    }

    private static String formatScore(Number score) {
        double value = score.doubleValue();
        if (value == Math.rint(value)) return NumberFormat.getIntegerInstance(Locale.US).format((long) value);
        return String.format(Locale.US, "%.1f", value);
    }
}
