package com.maximde.hologramlib.command;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.maximde.hologramlib.hologram.BlockHologram;
import com.maximde.hologramlib.hologram.Hologram;
import com.maximde.hologramlib.hologram.HologramManager;
import com.maximde.hologramlib.hologram.ItemHologram;
import com.maximde.hologramlib.hologram.MotionAnimation;
import com.maximde.hologramlib.hologram.PathAnimation;
import com.maximde.hologramlib.hologram.MotionAnimation.Keyframe;
import com.maximde.hologramlib.hologram.TextAnimation;
import com.maximde.hologramlib.hologram.TextHologram;
import com.maximde.hologramlib.hologram.Transition;
import com.maximde.hologramlib.hologram.custom.Backdrop;
import com.maximde.hologramlib.hologram.custom.HologramMenu;
import com.maximde.hologramlib.hologram.custom.ItemLabels;
import com.maximde.hologramlib.hologram.custom.LeaderboardHologram;
import com.maximde.hologramlib.hologram.custom.LeaderboardHologram.LeaderboardOptions;
import com.maximde.hologramlib.hologram.custom.LeaderboardHologram.LeaderboardType;
import com.maximde.hologramlib.hologram.custom.PagedHologram;
import com.maximde.hologramlib.hologram.custom.PagedLeaderboard;
import com.maximde.hologramlib.hologram.custom.ParticleHologram;
import com.maximde.hologramlib.hologram.custom.Podium;
import com.maximde.hologramlib.hologram.custom.ProgressBar;
import com.maximde.hologramlib.hologram.custom.TextLeaderboard;
import com.maximde.hologramlib.hologram.custom.TextLeaderboard.Entry;
import com.maximde.hologramlib.hologram.custom.TextLeaderboard.Toggle;
import com.maximde.hologramlib.hologram.effect.Shatter;
import com.maximde.hologramlib.hologram.effect.SoundCue;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

/**
 * {@code /holo demo}: spawns one station per hologram feature in a circle around the player, one after another.
 * Nothing is saved, {@link #clear()} or a restart removes it.
 */
public final class HologramDemo {

    private static final double RADIUS = 24;
    private static final double HEIGHT = 1.6;
    private static final long TICKS_BETWEEN_STATIONS = 10;
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final int BACKGROUND = 0x60000000;
    private static final List<String> FAMOUS_NAMES = List.of("Notch", "jeb_", "Dinnerbone", "Grumm", "Technoblade",
            "Dream", "GeorgeNotFound", "Sapnap", "Tommyinnit", "Ph1LzA");

    // spawner returns what removes the station again
    private record Station(String name, BiFunction<Location, Player, Runnable> spawner, double width) {
        Station(String name, BiFunction<Location, Player, Runnable> spawner) {
            this(name, spawner, 1);
        }
    }

    private static final UUID JEB = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    private final HologramManager manager;
    private final Plugin plugin;
    private final List<Station> stations;
    private final List<Runnable> removers = new ArrayList<>();
    private int generation;

    public HologramDemo(HologramManager manager, Plugin plugin) {
        this.manager = manager;
        this.plugin = plugin;
        this.stations = List.of(
                new Station("Text", (at, player) -> text(at)),
                new Station("Player head component", this::head),
                new Station("Spinning glowing item", (at, player) -> spinningItem(at)),
                new Station("Bouncing block", (at, player) -> bouncingBlock(at)),
                new Station("Floating", (at, player) -> withMotion(textHologram("demo_float", "<aqua><bold>I float"),
                        at, MotionAnimation.floating(0.3f, 40))),
                new Station("Pulse", (at, player) -> withMotion(item("demo_pulse", ItemTypes.HEART_OF_THE_SEA),
                        at, MotionAnimation.pulse(1.5f, 30))),
                new Station("Custom keyframes", (at, player) -> customKeyframes(at)),
                new Station("Typing", (at, player) -> animated("demo_typing", at,
                        TextAnimation.typing("<gold>", "HologramLib types this out"))),
                new Station("Moving gradient", (at, player) -> animated("demo_gradient", at,
                        TextAnimation.movingGradient("Moving gradient", 30, "#ff6000", "#ffc663"))),
                new Station("Reveal", (at, player) -> animated("demo_reveal", at,
                        TextAnimation.reveal("<light_purple>", "Secret message"))),
                new Station("Cycle", (at, player) -> animated("demo_cycle", at,
                        TextAnimation.cycle(20, "<red>Red", "<green>Green", "<blue>Blue", "<yellow>Yellow"))),
                new Station("Shine", (at, player) -> animated("demo_shine", at,
                        TextAnimation.shine("#55dce0", "#ffffff", "Shiny HologramLib"))),
                new Station("Scramble", (at, player) -> animated("demo_scramble", at,
                        TextAnimation.scramble("<green>", "Decoding the message"))),
                new Station("Rainbow", (at, player) -> animated("demo_rainbow", at, TextAnimation.rainbow("Rainbow text", 40))),
                new Station("Swing", (at, player) -> withMotion(textHologram("demo_swing", "<gold>Open 24/7")
                        .setScale(1.3f, 1.3f, 1.3f), at, MotionAnimation.swing(12, 50))),
                new Station("Transitions", (at, player) -> transitions(at)),
                new Station("Click me", (at, player) -> clickable(at)),
                new Station("Damage numbers, hit the dummy", (at, player) -> damageNumbers(at)),
                new Station("Countdown", (at, player) -> countdown(at)),
                new Station("Pages, click or wait", (at, player) -> pages(at)),
                new Station("Menu, look at it and click", (at, player) -> menu(at), 1.4),
                new Station("Flying along a path", (at, player) -> path(at), 1.6),
                new Station("Server goal", (at, player) -> serverGoal(at), 1.6),
                new Station("Particle text", (at, player) -> particleText(at), 2.4),
                new Station("Particle picture", (at, player) -> particlePicture(at)),
                new Station("Item labels, drop something", (at, player) -> itemLabels(at), 1.6),
                new Station("Text per player", (at, player) -> perPlayer(at)),
                new Station("Only while sneaking", (at, player) -> spawn(textHologram("demo_sneak",
                        "<green>You are sneaking").setViewCondition(Player::isSneaking).setUpdateTaskPeriod(5L), at)),
                new Station("Fixed, seen through blocks", (at, player) -> spawn(textHologram("demo_fixed",
                        "<white>Fixed rotation<newline><gray>visible through walls")
                        .setBillboard(Display.Billboard.FIXED).setSeeThroughBlocks(true), at)),
                new Station("Leaderboard", this::leaderboard, 1.4),
                new Station("Inline head leaderboard", this::inlineHeads, 1.4),
                new Station("Paged leaderboard, click the arrows", this::paged, 1.4),
                new Station("Text leaderboard, click the toggles", this::textLeaderboard, 2.6),
                new Station("Animated leaderboard, rows slide", this::animatedLeaderboard, 2.6),
                new Station("Departure board, rows flap", (at, player) -> departureBoard(at), 2.6),
                new Station("Every transition and row order", this::transitionBoard, 2.6),
                new Station("Podium", this::podium, 2.6),
                new Station("Letters fly apart, click", (at, player) -> explodingText(at)),
                new Station("Jelly, click it", (at, player) -> jelly(at)),
                new Station("Attached to you, press F5", this::attached));
    }

    /**
     * Removes a running demo and spawns it again around {@code player}.
     *
     * @return how many stations it spawns
     */
    public synchronized int start(Player player) {
        clear();
        int run = generation;
        Location center = player.getLocation();
        for (int index = 0; index < stations.size(); index++) {
            Station station = stations.get(index);
            Location spot = spot(center, index);
            int number = index + 1;
            BukkitTasks.runTaskLater(() -> spawnStation(run, number, station, spot, player),
                    1 + TICKS_BETWEEN_STATIONS * index);
        }
        return stations.size();
    }

    public synchronized void clear() {
        generation++;
        removers.forEach(Runnable::run);
        removers.clear();
    }

    private synchronized void spawnStation(int run, int number, Station station, Location spot, Player player) {
        // cleared or restarted meanwhile
        if (run != generation) return;
        removers.add(withMotion(textHologram("demo_label_" + number, "<gray>" + number + ". <white>" + station.name())
                .setScale(0.7f, 0.7f, 0.7f), spot.clone().add(0, 0.3, 0), MotionAnimation.popIn(8)));
        removers.add(station.spawner().apply(spot.clone().add(0, HEIGHT, 0), player));
    }

    // each station gets a slice as wide as it is, first one straight ahead
    private Location spot(Location center, int index) {
        double total = stations.stream().mapToDouble(Station::width).sum();
        double before = stations.subList(0, index).stream().mapToDouble(Station::width).sum();
        double middle = before + (stations.get(index).width() - stations.get(0).width()) / 2;
        double angle = Math.toRadians(center.getYaw()) + 2 * Math.PI * middle / total;
        Location spot = center.clone().add(-Math.sin(angle) * RADIUS, 0, Math.cos(angle) * RADIUS);
        spot.setDirection(center.toVector().subtract(spot.toVector()));
        spot.setPitch(0);
        return spot;
    }

    private Runnable text(Location at) {
        return withMotion(textHologram("demo_text", """
                <gradient:#ff6000:#ffc663><bold>HologramLib</bold></gradient>
                <gray>MiniMessage, <rainbow>colors</rainbow>, <u>styles</u>
                <gray>and as many lines as you like""").setScale(1.3f, 1.3f, 1.3f), at, MotionAnimation.popIn(10));
    }

    private Runnable head(Location at, Player player) {
        return spawn(textHologram("demo_head", "<head:" + player.getName() + "> <white>" + player.getName()
                + "<newline><gray>no resource pack, 1.21.9+ clients"), at);
    }

    private Runnable spinningItem(Location at) {
        ItemHologram item = item("demo_spin", ItemTypes.NETHER_STAR).setGlowing(true).setGlowColor(Color.ORANGE);
        return withMotion(item, at, MotionAnimation.spin(60));
    }

    private Runnable bouncingBlock(Location at) {
        int grass = WrappedBlockState.getByString(Bukkit.createBlockData(Material.GRASS_BLOCK).getAsString()).getGlobalId();
        // block displays grow from the corner
        BlockHologram block = new BlockHologram("demo_bounce").setBlock(grass)
                .setScale(0.6f, 0.6f, 0.6f).setTranslation(-0.3f, 0, -0.3f);
        return withMotion(block, at, MotionAnimation.bounce(0.6f, 24));
    }

    private Runnable customKeyframes(Location at) {
        MotionAnimation motion = MotionAnimation.builder()
                .keyframe(new Keyframe(new Vector3f(0, 0.5f, 0), new Quaternionf().rotateY((float) Math.toRadians(120)),
                        new Vector3f(1.4f, 1.4f, 1.4f), 20))
                .keyframe(new Keyframe(new Vector3f(0, 0.2f, 0), new Quaternionf().rotateY((float) Math.toRadians(240)),
                        new Vector3f(0.8f, 0.8f, 0.8f), 20))
                .keyframe(Keyframe.offset(0, 0, 0, 20))
                .loop(true)
                .build();
        return withMotion(item("demo_keyframes", ItemTypes.DIAMOND_SWORD), at, motion);
    }

    private Runnable clickable(Location at) {
        AtomicInteger clicks = new AtomicInteger();
        TextHologram button = textHologram("demo_click", clickText(0)).setScale(1.2f, 1.2f, 1.2f);
        button.onInteract(player -> {
            int count = clicks.incrementAndGet();
            button.setMiniMessageText(clickText(count)).updateText();
            player.playSound(player.getLocation(), "block.note_block.pling", 1, 0.5f + (count % 12) / 8f);
            manager.applyMotion(button, MotionAnimation.shake(0.08f, 10));
        });
        return spawn(button, at);
    }

    private Runnable transitions(Location at) {
        List<Transition> shown = List.of(Transition.FLIP, Transition.TURN, Transition.SQUASH, Transition.POP,
                Transition.SLIDE);
        TextHologram hologram = textHologram("demo_transition", transitionText(shown.get(0))).setScale(1.3f, 1.3f, 1.3f);
        Runnable remover = spawn(hologram, at);
        AtomicInteger next = new AtomicInteger();
        TaskHandle task = BukkitTasks.runTaskTimer(() -> {
            Transition transition = shown.get(next.getAndIncrement() % shown.size());
            Transition following = shown.get(next.get() % shown.size());
            manager.transition(hologram, transition, 6, 1.6f, 0.65f,
                    () -> hologram.setMiniMessageText(transitionText(following)).updateText());
        }, 60L, 60L);
        return () -> {
            task.cancel();
            remover.run();
        };
    }

    private Runnable damageNumbers(Location at) {
        AtomicInteger hits = new AtomicInteger();
        TextHologram dummy = textHologram("demo_dummy", "<red>❤ <white>Training dummy<newline><gray>Hit or click me")
                .setScale(1.2f, 1.2f, 1.2f);
        dummy.onInteract(player -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boolean critical = random.nextInt(4) == 0;
            int damage = critical ? 12 + random.nextInt(9) : 2 + random.nextInt(7);
            player.playSound(player.getLocation(), critical ? "entity.player.attack.crit" : "entity.player.attack.strong", 1, 1);
            manager.applyMotion(dummy, MotionAnimation.shake(0.05f, 6));
            popDamage(at.clone().add(random.nextDouble(-0.5, 0.5), 0.2, random.nextDouble(-0.5, 0.5)),
                    hits.incrementAndGet(), critical ? "<gold><bold>" + damage + "!" : "<red>-" + damage);
        });
        return spawn(dummy, at);
    }

    private void popDamage(Location at, int hit, String text) {
        TextHologram number = new TextHologram("demo_damage_" + hit).setMiniMessageText(text).setShadow(true)
                .setScale(1.2f, 1.2f, 1.2f);
        manager.spawn(number, at, false);
        Quaternionf upright = new Quaternionf();
        manager.applyMotion(number, MotionAnimation.of(false,
                new Keyframe(new Vector3f(0, 0.25f, 0), upright, new Vector3f(1.4f, 1.4f, 1.4f), 3),
                new Keyframe(new Vector3f(0, 0.8f, 0), upright, new Vector3f(1, 1, 1), 12),
                new Keyframe(new Vector3f(0, 1, 0), upright, new Vector3f(0, 0, 0), 5)));
        BukkitTasks.runTaskLater(() -> manager.remove(number), 25L);
    }

    private Runnable countdown(Location at) {
        int from = 10;
        TextHologram hologram = textHologram("demo_countdown", countdownText(from)).setScale(1.3f, 1.3f, 1.3f);
        Runnable remover = spawn(hologram, at);
        AtomicInteger left = new AtomicInteger(from);
        TaskHandle task = BukkitTasks.runTaskTimer(() -> {
            int seconds = left.updateAndGet(current -> current == 0 ? from : current - 1);
            hologram.setMiniMessageText(seconds == 0 ? "<gold><bold>GO!" : countdownText(seconds)).updateText();
            if (seconds == 0) manager.applyMotion(hologram, MotionAnimation.popIn(8));
            else if (seconds <= 3) manager.applyMotion(hologram, MotionAnimation.of(false,
                    Keyframe.scaled(1.25f, 1.25f, 1.25f, 2), Keyframe.scaled(1, 1, 1, 6)));
        }, 20L, 20L);
        return () -> {
            task.cancel();
            remover.run();
        };
    }

    private Runnable pages(Location at) {
        PagedHologram paged = new PagedHologram("demo_pages")
                .addPage("<#55dce0><bold>Welcome!</bold><newline><gray>Click to turn the page")
                .addPage("<gold><bold>Rules</bold><newline><white>1. Be nice<newline>2. Have fun")
                .addPage("<light_purple><bold>Discord</bold><newline><white>discord.gg/example")
                .setAutoTurnTicks(100);
        paged.getHologram().setBackgroundColor(BACKGROUND);
        paged.spawn(manager, at);
        return () -> paged.remove(manager);
    }

    // clicks only change what the clicker sees
    private Runnable menu(Location at) {
        HologramMenu menu = new HologramMenu("demo_menu")
                .addButton("Make it day", player -> player.setPlayerTime(6000, false))
                .addButton("Make it night", player -> player.setPlayerTime(18000, false))
                .addButton("Back to the real time", Player::resetPlayerTime)
                .addButton("Jump", player -> player.setVelocity(new Vector(0, 0.9, 0)));
        menu.spawn(manager, at.clone().subtract(0, 0.6, 0));
        return () -> menu.remove(manager);
    }

    private Runnable path(Location at) {
        TextHologram flyer = textHologram("demo_path", "<gold><bold>Wheee!");
        Runnable remover = spawn(flyer, at);
        List<Location> points = new ArrayList<>();
        for (int point = 0; point < 8; point++) {
            double angle = 2 * Math.PI * point / 8;
            points.add(at.clone().add(Math.sin(angle) * 1.2, Math.sin(angle * 2) * 0.3, Math.sin(angle * 2) * 0.6));
        }
        AtomicReference<TaskHandle> flight = new AtomicReference<>();
        // spawn is a tick late
        BukkitTasks.runTaskLater(() -> flight.set(PathAnimation.through(points, 10, true).play(flyer)), 2L);
        return () -> {
            Optional.ofNullable(flight.get()).ifPresent(TaskHandle::cancel);
            remover.run();
        };
    }

    // fake votes every two seconds
    private Runnable serverGoal(Location at) {
        ProgressBar bar = new ProgressBar("demo_goal")
                .setGoal(1000)
                .setLabelFormat("<#55dce0><bold>Vote goal</bold><newline><white>{value} <gray>/ {goal} votes");
        bar.spawn(manager, at.clone().subtract(0, 0.3, 0));
        TaskHandle votes = BukkitTasks.runTaskTimer(() -> {
            double next = bar.getValue() + ThreadLocalRandom.current().nextInt(60, 180);
            bar.setProgress(next > 1000 ? 0 : next, next > 1000 ? 10 : 20);
        }, 20L, 40L);
        return () -> {
            votes.cancel();
            bar.remove(manager);
        };
    }

    private Runnable particleText(Location at) {
        ParticleHologram particles = ParticleHologram.text("HologramLib", 12, new Color(0x55dce0)).setPixelSize(0.035f);
        particles.spawn(at.clone().subtract(0, 0.3, 0));
        return particles::remove;
    }

    private Runnable particlePicture(Location at) {
        String[] heart = {
                ".RR...RR.",
                "RWRR.RRRR",
                "RWRRRRRRR",
                "RRRRRRRRR",
                ".RRRRRRR.",
                "..RRRRR..",
                "...RRR...",
                "....R...."};
        BufferedImage image = new BufferedImage(heart[0].length(), heart.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < heart.length; y++) {
            for (int x = 0; x < heart[y].length(); x++) {
                char pixel = heart[y].charAt(x);
                if (pixel == '.') continue;
                image.setRGB(x, y, pixel == 'W' ? 0xffffffff : 0xffe0204a);
            }
        }
        ParticleHologram particles = ParticleHologram.image(image).setPixelSize(0.09f);
        particles.spawn(at.clone().subtract(0, 0.4, 0));
        return particles::remove;
    }

    // labels drops while the demo runs, plus a few items nobody can pick up
    private Runnable itemLabels(Location at) {
        ItemLabels labels = new ItemLabels(manager, plugin).setLabelAllDrops(true);
        Runnable removeHint = spawn(textHologram("demo_item_hint", "<gray>Press <white>Q</white> to drop an item"), at);
        List<Item> dropped = new ArrayList<>();
        Location ground = at.clone().subtract(0, HEIGHT - 0.5, 0);
        BukkitTasks.runTask(() -> {
            org.bukkit.inventory.ItemStack excalibur = new org.bukkit.inventory.ItemStack(Material.NETHERITE_SWORD);
            ItemMeta meta = excalibur.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6§lExcalibur");
                excalibur.setItemMeta(meta);
            }
            List<org.bukkit.inventory.ItemStack> stacks = List.of(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 3),
                    new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE), excalibur);
            for (int index = 0; index < stacks.size(); index++) {
                Item item = ground.getWorld().dropItem(ground.clone().add((index - 1) * 0.6, 0, 0), stacks.get(index));
                item.setVelocity(new Vector());
                item.setPickupDelay(Short.MAX_VALUE);
                item.setUnlimitedLifetime(true);
                dropped.add(item);
            }
        }, ground);
        return () -> {
            labels.close();
            removeHint.run();
            dropped.forEach(item -> BukkitTasks.runTask(item::remove, item));
        };
    }

    private static String countdownText(int seconds) {
        String color = seconds <= 3 ? "<#d98afa>" : "<#55dce0>";
        return String.format(Locale.ROOT, "<gray>Event starts in<newline>%s<bold>00:%02d", color, seconds);
    }

    private static String transitionText(Transition transition) {
        return "<white>Transition<newline><#55dce0><bold>" + transition.name().toLowerCase(Locale.ROOT);
    }

    private static String clickText(int clicks) {
        return "<green><bold>Click me!</bold><newline><gray>" + clicks + (clicks == 1 ? " click" : " clicks");
    }

    private Runnable perPlayer(Location at) {
        TextHologram hologram = textHologram("demo_per_player", "").setTextProvider(viewer ->
                "<aqua>Hi <white>" + viewer.getName() + "</white>, only you see this"
                        + "<newline><gray>Ping <white>" + viewer.getPing() + " ms"
                        + "<newline><gray>" + LocalTime.now().truncatedTo(ChronoUnit.SECONDS));
        return spawn(hologram, at);
    }

    private Runnable leaderboard(Location at, Player player) {
        LeaderboardHologram board = new LeaderboardHologram(LeaderboardOptions.builder()
                .title("Top Players").suffix("kills").maxDisplayEntries(5)
                .leaderboardType(LeaderboardType.TOP_PLAYER_HEAD).build(), "demo_leaderboard");
        fillScores(board, player);
        manager.spawn(board, at.clone().subtract(0, HEIGHT - 0.8, 0));
        return () -> manager.remove(board);
    }

    private Runnable inlineHeads(Location at, Player player) {
        LeaderboardHologram board = new LeaderboardHologram(LeaderboardOptions.builder()
                .title("Inline Heads").maxDisplayEntries(5)
                .leaderboardType(LeaderboardType.ALL_PLAYER_HEADS).build(), "demo_inline");
        fillScores(board, player);
        manager.spawn(board, at.clone().subtract(0, HEIGHT - 0.8, 0));
        return () -> manager.remove(board);
    }

    private Runnable paged(Location at, Player player) {
        PagedLeaderboard paged = new PagedLeaderboard("demo_paged");
        for (String title : List.of("Kills", "Deaths", "Coins")) {
            LeaderboardHologram page = new LeaderboardHologram(LeaderboardOptions.builder()
                    .title(title).maxDisplayEntries(5).leaderboardType(LeaderboardType.SIMPLE_TEXT).build(),
                    "demo_paged_" + title.toLowerCase());
            fillScores(page, player);
            paged.addPage(page);
        }
        manager.spawn(paged, at.clone().subtract(0, HEIGHT - 0.8, 0));
        return () -> manager.remove(paged);
    }

    private Runnable textLeaderboard(Location at, Player player) {
        List<String> stats = List.of("Eliminations", "Wins", "Playtime");
        Map<String, List<Entry>> scores = new HashMap<>();
        for (String stat : stats) {
            for (String period : List.of("Daily", "Weekly", "Lifetime")) {
                scores.put(stat + period, fakeEntries(player, stat + period));
            }
        }
        TextLeaderboard board = new TextLeaderboard("demo_text_leaderboard")
                .setWidth(220)
                .setTitle(view -> {
                    String stat = stats.get(view.cycle() % stats.size());
                    return statIcon(stat) + " <aqua>Sky Battle - " + stat + " - <yellow>" + view.state("period");
                })
                .setEntries(view -> {
                    List<Entry> entries = scores.get(stats.get(view.cycle() % stats.size()) + view.state("period"));
                    if (!view.state("scope").equals("Friends")) return entries;
                    return entries.stream().filter(entry -> isFriend(entry, view.viewer())).toList();
                })
                .setAroundViewer(view -> view.state("rows").equals("Around You"))
                .setFooter(view -> "<yellow>Leaderboard will update in " + (60 - LocalTime.now().getSecond()) + "s")
                .setBackdrop(new Backdrop("demo_text_leaderboard_backdrop")
                        .setBackground(Material.BLACK_CONCRETE)
                        .setBorder(Material.WHITE_CONCRETE)
                        .setProgressBar(Material.GRAY_CONCRETE, Material.WHITE_CONCRETE))
                .setCycle(200)
                .setTransition(Transition.FLIP)
                .setToggleScale(0.75f)
                .addToggle(Toggle.of("period", "Daily", "Weekly", "Lifetime"))
                .addToggle(Toggle.of("rows", "Top Ten", "Around You"))
                .addToggle(Toggle.of("scope", "Global", "Friends"));
        board.getBoard().setScale(0.9f, 0.9f, 0.9f);
        manager.spawn(board, at.clone().subtract(0, 0.2, 0));
        return () -> manager.remove(board);
    }

    private Runnable animatedLeaderboard(Location at, Player player) {
        List<String> stats = List.of("Kills", "Deaths", "Playtime");
        Map<String, List<Entry>> scores = new HashMap<>();
        stats.forEach(stat -> scores.put(stat, fakeEntries(player, "animated" + stat)));
        TextLeaderboard board = new TextLeaderboard("demo_animated_leaderboard")
                .setWidth(225)
                .setSplitRows(true)
                .setTransition(Transition.SLIDE)
                .setTransitionTicks(5)
                .setRowRevealTicks(1)
                .setCycle(160)
                .setRowFormat("{place_color}{place}. {head} {name} {fill} <#ffd263>{score}")
                .setOwnRowFormat(null)
                .setPlaceColors(List.of("#fffb00", "#cfcfcf", "#d3854d"))
                .setOtherPlaceColor("#aaaaaa")
                .setFillColor("#3a3f4b")
                .setNameFormat(entry -> {
                    String name = MiniMessage.miniMessage().escapeTags(entry.name());
                    return looksOnline(entry) ? "<gradient:#a745ff:#cf97ff:#a745ff>" + name + "</gradient>" : "<white>" + name;
                })
                .setTitle(view -> animatedTitle(stats.get(view.cycle() % stats.size())))
                .setEntries(view -> scores.get(stats.get(view.cycle() % stats.size())))
                .setFooter("<gray>Online players in <gradient:#a745ff:#cf97ff>purple")
                .setBackdrop(new Backdrop("demo_animated_leaderboard_backdrop")
                        .setBackground(Material.BLACK_CONCRETE)
                        .setBorder(Material.WHITE_CONCRETE)
                        .setProgressBar(Material.GRAY_CONCRETE, Material.WHITE_CONCRETE));
        board.getBoard().setScale(0.9f, 0.9f, 0.9f);
        manager.spawn(board, at.clone().subtract(0, 0.6, 0));
        return () -> manager.remove(board);
    }

    // times of day as scores so flights sort by departure
    private Runnable departureBoard(Location at) {
        List<String> cities = List.of("Berlin", "Tokyo", "New York", "Reykjavik", "Sydney", "Cairo", "Lima",
                "Oslo", "Seoul", "Lisbon", "Nairobi", "Vancouver", "Dubai", "Rome");
        String amber = "#ffb000";
        TextLeaderboard board = new TextLeaderboard("demo_departures")
                .setWidth(200)
                .setSplitRows(true)
                .setTransition(Transition.FLAP)
                .setTransitionTicks(4)
                .setRowRevealTicks(1)
                .setCycle(120)
                .setAscending(true)
                .setMaxRows(8)
                .setRowFormat("<" + amber + ">{name} {fill} <" + amber + ">{score}")
                .setOwnRowFormat(null)
                .setFillColor("#3a2a00")
                .setScoreFormatter(minutes -> String.format(Locale.ROOT, "%02d:%02d",
                        minutes.intValue() / 60 % 24, minutes.intValue() % 60))
                .setTitle("<" + amber + "><bold>DEPARTURES")
                .setFooter("<#7a5a00>Next update in a few seconds")
                .setEntries(view -> {
                    List<Entry> flights = new ArrayList<>();
                    for (int index = 0; index < 8; index++) {
                        String city = cities.get((view.cycle() * 3 + index) % cities.size());
                        int minutes = 8 * 60 + view.cycle() * 25 + index * 15;
                        flights.add(new Entry(UUID.nameUUIDFromBytes(city.getBytes(StandardCharsets.UTF_8)), city, minutes));
                    }
                    return flights;
                })
                .setBackdrop(new Backdrop("demo_departures_backdrop")
                        .setBackground(Material.BLACK_CONCRETE)
                        .setBorder(Material.GRAY_CONCRETE)
                        .setProgressBar(Material.BLACK_CONCRETE, Material.ORANGE_CONCRETE));
        manager.spawn(board, at.clone().subtract(0, 0.6, 0));
        return () -> manager.remove(board);
    }

    // next transition and row order right before each cycle
    private Runnable transitionBoard(Location at, Player player) {
        List<Transition> transitions = List.of(Transition.SLIDE, Transition.FLAP, Transition.DROP, Transition.SPIN,
                Transition.FLIP, Transition.TURN, Transition.SQUASH, Transition.POP);
        List<TextLeaderboard.RowOrder> orders = List.of(TextLeaderboard.RowOrder.values());
        int period = 100;
        TextLeaderboard board = new TextLeaderboard("demo_transition_board")
                .setWidth(200)
                .setSplitRows(true)
                .setTransition(transitions.get(0))
                .setTransitionTicks(5)
                .setRowRevealTicks(1)
                .setCycle(period)
                .setMaxRows(8)
                .setRowFormat("{place_color}{place}. {head} <white>{name} {fill} <#55dce0>{score}")
                .setOwnRowFormat(null)
                .setPlaceColors(List.of("#fffb00", "#cfcfcf", "#d3854d"))
                .setOtherPlaceColor("#aaaaaa")
                .setFillColor("#3a3f4b")
                .setEntries(view -> fakeEntries(player, "transitions" + view.cycle() % 4));
        board.setTitle(view -> "<#55dce0><bold>" + board.getTransition().name() + "</bold> <gray>"
                + board.getRowOrder().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        board.setBackdrop(new Backdrop("demo_transition_board_backdrop")
                .setBackground(Material.BLACK_CONCRETE)
                .setBorder(Material.CYAN_CONCRETE)
                .setProgressBar(Material.GRAY_CONCRETE, Material.CYAN_CONCRETE));
        manager.spawn(board, at.clone().subtract(0, 0.6, 0));
        AtomicInteger next = new AtomicInteger(1);
        TaskHandle picker = BukkitTasks.runTaskTimer(() -> {
            int index = next.getAndIncrement();
            board.setTransition(transitions.get(index % transitions.size()))
                    .setRowOrder(orders.get(index % orders.size()));
        }, period - 5L, period);
        return () -> {
            picker.cancel();
            manager.remove(board);
        };
    }

    private Runnable explodingText(Location at) {
        Shatter shatter = new Shatter(manager);
        return regrowing(at, round -> new TextHologram("demo_explode_" + round)
                        .setMiniMessageText("<gradient:#ff4d00:#ffd000><bold>BOOM!").setBackgroundColor(0).setShadow(true)
                        .setScale(2.2f, 2.2f, 2.2f),
                (text, player) -> shatter.explode(text, player.getLocation().getYaw() + 180), MotionAnimation.popIn(8));
    }

    // a click breaks it, a new one grows back a few seconds later
    private <H extends Hologram<H>> Runnable regrowing(Location at, IntFunction<H> make, BiConsumer<H, Player> breakApart,
                                                       MotionAnimation entrance) {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<H> shown = new AtomicReference<>();
        AtomicInteger round = new AtomicInteger();
        Runnable[] grow = new Runnable[1];
        grow[0] = () -> {
            if (!running.get()) return;
            H hologram = make.apply(round.incrementAndGet());
            hologram.onInteract(player -> {
                if (!shown.compareAndSet(hologram, null)) return;
                breakApart.accept(hologram, player);
                BukkitTasks.runTaskLater(grow[0], 80L);
            });
            shown.set(hologram);
            manager.spawn(hologram, at, false);
            if (entrance != null) manager.applyMotion(hologram, entrance);
        };
        grow[0].run();
        return () -> {
            running.set(false);
            Optional.ofNullable(shown.getAndSet(null)).ifPresent(manager::remove);
        };
    }

    private Runnable jelly(Location at) {
        TextHologram jelly = new TextHologram("demo_jelly").setMiniMessageText("<#7ee06a><bold>Jelly")
                .setBackgroundColor(0).setShadow(true).setScale(2.2f, 2.2f, 2.2f);
        jelly.onInteract(player -> {
            manager.applyMotion(jelly, MotionAnimation.jelly(0.4f, 24));
            SoundCue.BOUNCE.play(at);
        });
        return spawn(jelly, at);
    }

    private static int blockState(Material material) {
        return WrappedBlockState.getByString(Bukkit.createBlockData(material).getAsString()).getGlobalId();
    }

    private Runnable podium(Location at, Player player) {
        Podium podium = new Podium("demo_podium").setEntries(List.of(
                new Podium.Entry(player.getUniqueId(), player.getName(), "1,337 wins"),
                new Podium.Entry(NOTCH, "Notch", "1,024 wins"),
                new Podium.Entry(JEB, "jeb_", "777 wins")));
        // away from the label, it would end up in the middle pedestal
        podium.spawn(manager, at.clone().subtract(0, HEIGHT, 0).subtract(at.getDirection().multiply(1.2)));
        return () -> podium.remove(manager);
    }

    private static String animatedTitle(String stat) {
        String color = switch (stat) {
            case "Kills" -> "#ee2a2a";
            case "Deaths" -> "#c22a2a";
            default -> "#ffe344";
        };
        return statIcon(stat) + " <bold><" + color + ">TOP " + stat.toUpperCase(Locale.ROOT);
    }

    // online players plus a few fake ones
    private static boolean looksOnline(Entry entry) {
        return Bukkit.getPlayer(entry.uuid()) != null || List.of("jeb_", "Dream", "Technoblade").contains(entry.name());
    }

    // one clock sprite per frame, picking by second turns the hand
    private static String statIcon(String stat) {
        return switch (stat) {
            case "Eliminations", "Kills" -> "<sprite:items:item/diamond_sword>";
            case "Wins" -> "<sprite:items:item/emerald>";
            case "Deaths" -> "<sprite:items:item/bone>";
            default -> String.format(Locale.ROOT, "<sprite:items:item/clock_%02d>", System.currentTimeMillis() / 1000 % 64);
        };
    }

    // same scores every time, with ties
    private static List<Entry> fakeEntries(Player player, String board) {
        Random random = new Random(board.hashCode());
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(player.getUniqueId(), player.getName(), random.nextInt(25), "<gray>[<gold>VIP<gray>] "));
        for (int index = 0; index < 29; index++) {
            String name = index < FAMOUS_NAMES.size() ? FAMOUS_NAMES.get(index) : "Player" + index;
            String rank = index % 4 == 0 ? "<gray>[<aqua>MVP<gray>] " : "";
            entries.add(new Entry(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name, random.nextInt(25), rank));
        }
        return List.copyOf(entries);
    }

    private static boolean isFriend(Entry entry, Player viewer) {
        return entry.uuid().equals(viewer.getUniqueId()) || Math.floorMod(entry.uuid().hashCode(), 3) == 0;
    }

    private void fillScores(LeaderboardHologram board, Player player) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        board.setPlayerScore(player.getUniqueId(), player.getName(), 1000L + random.nextInt(500));
        board.setPlayerScore(NOTCH, "Notch", 500L + random.nextInt(400));
        for (String name : List.of("jeb_", "Dinnerbone", "Grumm", "Technoblade")) {
            board.setPlayerScore(UUID.randomUUID(), name, random.nextInt(500));
        }
    }

    private Runnable attached(Location at, Player player) {
        TextHologram tag = textHologram("demo_attached", "<yellow>★ <white>" + player.getName() + " <yellow>★")
                .setTranslation(0, 0.3f, 0);
        manager.spawn(tag, at);
        // client drops passengers for entities it doesn't have yet
        BukkitTasks.runTaskLater(() -> manager.attach(tag, player.getEntityId()), 5L);
        Runnable removeHint = spawn(textHologram("demo_attached_hint", "<gray>Look above your head"), at);
        return () -> {
            removeHint.run();
            manager.remove(tag);
        };
    }

    private Runnable animated(String id, Location at, TextAnimation animation) {
        TextHologram hologram = textHologram(id, "");
        manager.spawn(hologram, at);
        manager.applyAnimation(hologram, animation);
        return () -> manager.remove(hologram);
    }

    private <H extends Hologram<H>> Runnable withMotion(H hologram, Location at, MotionAnimation motion) {
        Runnable remover = spawn(hologram, at);
        manager.applyMotion(hologram, motion);
        return remover;
    }

    private <H extends Hologram<H>> Runnable spawn(H hologram, Location at) {
        manager.spawn(hologram, at, false);
        return () -> manager.remove(hologram);
    }

    private static TextHologram textHologram(String id, String text) {
        return new TextHologram(id).setMiniMessageText(text).setBackgroundColor(BACKGROUND);
    }

    private static ItemHologram item(String id, ItemType type) {
        return new ItemHologram(id).setItem(new ItemStack.Builder().type(type).build());
    }
}
