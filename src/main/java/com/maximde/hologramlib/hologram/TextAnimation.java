package com.maximde.hologramlib.hologram;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class TextAnimation {

    // The animation task reads the frames while the plugin may still add some, on Folia from another thread.
    private final List<String> textFrames = new CopyOnWriteArrayList<>();

    /**
     * The display will be updated every x ticks
     * 20 ticks = 1 second
     */
    private @Setter @Accessors(chain = true) long speed = 20;

    /**
     * Delay in ticks when the animation should start
     * 20 ticks = 1 second
     */
    private @Setter @Accessors(chain = true) long delay = 20;


    public TextAnimation() {}

    public TextAnimation(int speed) {
        this.speed = speed;
    }

    public TextAnimation(int speed, int delay) {
        this.speed = speed;
        this.delay = delay;
    }

    /**
     * Types {@code text} one character per frame, then holds the full text for a moment.
     *
     * @param style MiniMessage tags put in front of every frame, like {@code "<gold><bold>"}
     * @param text  plain text, tags in it are shown as written
     */
    public static TextAnimation typing(String style, String text) {
        TextAnimation animation = new TextAnimation(2, 1);
        for (int length = 1; length <= text.length(); length++) {
            String cursor = length < text.length() ? "_" : "";
            animation.addFrame(style + escape(text.substring(0, length)) + cursor);
        }
        for (int hold = 0; hold < 20; hold++) {
            animation.addFrame(style + escape(text));
        }
        return animation;
    }

    /**
     * Shows {@code text} and nothing in turns.
     *
     * @param text MiniMessage text
     */
    public static TextAnimation blink(String text) {
        return new TextAnimation(10, 1)
                .addFrame(text)
                .addFrame("");
    }

    /**
     * Moves a gradient across {@code text} in {@code steps} frames, then starts over. The colors form a loop, the
     * last one fades back into the first, so there is no jump when the animation starts over.
     *
     * @param text   plain text, tags in it are shown as written
     * @param colors at least two MiniMessage colors, like {@code "#ff5555"} or {@code "gold"}
     */
    public static TextAnimation movingGradient(String text, int steps, String... colors) {
        if (colors.length < 2) throw new IllegalArgumentException("A gradient needs at least two colors");
        if (steps < 1) throw new IllegalArgumentException("A gradient needs at least one step");

        // minimessage gradients jump once per loop, so own colors per char
        TextColor[] palette = Arrays.stream(colors).map(TextAnimation::parseColor).toArray(TextColor[]::new);
        int[] characters = text.codePoints().toArray();
        TextAnimation animation = new TextAnimation(1, 1);
        for (int step = 0; step < steps; step++) {
            StringBuilder frame = new StringBuilder();
            for (int index = 0; index < characters.length; index++) {
                double position = (double) index / characters.length - (double) step / steps;
                appendColored(frame, colorAt(palette, position), characters[index]);
            }
            animation.addFrame(frame.toString());
        }
        return animation;
    }

    private static TextColor colorAt(TextColor[] palette, double position) {
        double scaled = (position - Math.floor(position)) * palette.length;
        int low = (int) scaled;
        return TextColor.lerp((float) (scaled - low), palette[low], palette[(low + 1) % palette.length]);
    }

    private static TextColor parseColor(String color) {
        TextColor parsed = color.startsWith("#") ? TextColor.fromHexString(color) : NamedTextColor.NAMES.value(color);
        if (parsed == null) throw new IllegalArgumentException("Unknown color " + color);
        return parsed;
    }

    /**
     * Starts fully obfuscated and reveals {@code text} from left to right, then holds it for a moment.
     *
     * @param style MiniMessage tags put in front of every frame, like {@code "<aqua>"}
     * @param text  plain text, tags in it are shown as written
     */
    public static TextAnimation reveal(String style, String text) {
        TextAnimation animation = new TextAnimation(2, 1);
        for (int revealed = 0; revealed <= text.length(); revealed++) {
            String hidden = text.substring(revealed);
            animation.addFrame(style + escape(text.substring(0, revealed))
                    + (hidden.isEmpty() ? "" : "<obf>" + escape(hidden) + "</obf>"));
        }
        for (int hold = 0; hold < 20; hold++) {
            animation.addFrame(style + escape(text));
        }
        return animation;
    }

    /**
     * Shows each of {@code texts} for {@code ticksEach} ticks in turns, like a slideshow.
     *
     * @param texts MiniMessage texts
     */
    public static TextAnimation cycle(int ticksEach, String... texts) {
        TextAnimation animation = new TextAnimation(ticksEach, 1);
        for (String text : texts) {
            animation.addFrame(text);
        }
        return animation;
    }

    /**
     * A gleam of {@code shineColor} sweeps across {@code text} in {@code baseColor} from left to right, then the text
     * rests for a moment, like light catching a sign.
     *
     * @param text plain text, tags in it are shown as written
     */
    public static TextAnimation shine(String baseColor, String shineColor, String text) {
        TextColor base = parseColor(baseColor);
        TextColor shine = parseColor(shineColor);
        int[] characters = text.codePoints().toArray();
        double gleamWidth = 3;
        TextAnimation animation = new TextAnimation(1, 1);
        for (double center = -gleamWidth; center <= characters.length + gleamWidth; center += 0.5) {
            StringBuilder frame = new StringBuilder();
            for (int index = 0; index < characters.length; index++) {
                float strength = (float) Math.max(0, 1 - Math.abs(index - center) / gleamWidth);
                appendColored(frame, TextColor.lerp(strength, base, shine), characters[index]);
            }
            animation.addFrame(frame.toString());
        }
        String resting = "<" + base.asHexString() + ">" + escape(text);
        for (int hold = 0; hold < 40; hold++) {
            animation.addFrame(resting);
        }
        return animation;
    }

    /**
     * Starts as random characters that settle into {@code text} one by one from the left, like a decoder, then holds
     * the text for a moment.
     *
     * @param style MiniMessage tags put in front of every frame, like {@code "<green>"}
     * @param text  plain text, tags in it are shown as written
     */
    public static TextAnimation scramble(String style, String text) {
        String noise = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&@";
        // fixed seed so the same text scrambles the same
        Random random = new Random(text.hashCode());
        int[] characters = text.codePoints().toArray();
        TextAnimation animation = new TextAnimation(1, 1);
        int framesPerCharacter = 2;
        for (int frameIndex = 0; frameIndex <= characters.length * framesPerCharacter; frameIndex++) {
            int settled = frameIndex / framesPerCharacter;
            StringBuilder frame = new StringBuilder(style);
            for (int index = 0; index < characters.length; index++) {
                boolean keep = index < settled || Character.isWhitespace(characters[index]);
                int character = keep ? characters[index] : noise.charAt(random.nextInt(noise.length()));
                frame.append(escape(Character.toString(character)));
            }
            animation.addFrame(frame.toString());
        }
        for (int hold = 0; hold < 40; hold++) {
            animation.addFrame(style + escape(text));
        }
        return animation;
    }

    /**
     * {@link #movingGradient} through every color of the rainbow.
     */
    public static TextAnimation rainbow(String text, int steps) {
        return movingGradient(text, steps, "#ff5555", "#ffaa00", "#ffff55", "#55ff55", "#55ffff", "#5555ff", "#ff55ff");
    }

    private static void appendColored(StringBuilder frame, TextColor color, int character) {
        frame.append('<').append(color.asHexString()).append('>').append(escape(Character.toString(character)));
    }

    private static String escape(String text) {
        return MiniMessage.miniMessage().escapeTags(text);
    }

    public TextAnimation addFrame(String text) {
        this.textFrames.add(text);
        return this;
    }

    public TextAnimation removeFrame(int number) {
        this.textFrames.remove(number);
        return this;
    }

    public TextAnimation removeLastFrame() {
        removeFrame(this.textFrames.size() - 1);
        return this;
    }

    public TextAnimation removeFirstFrame() {
        removeFrame(0);
        return this;
    }

    public TextAnimation clearFrames() {
        this.textFrames.clear();
        return this;
    }

}
