package com.maximde.hologramlib.persistence.importer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LegacyColors {

    private static final Pattern GRADIENT = Pattern.compile("<#([0-9a-fA-F]{6})>(.*?)</#([0-9a-fA-F]{6})>");
    private static final Pattern BRACED_HEX = Pattern.compile("\\{#([0-9a-fA-F]{6})}");
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§](#[0-9a-fA-F]{6}|x(?:[&§][0-9a-fA-F]){6}|[0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"), Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private LegacyColors() {}

    static String toMiniMessage(String text) {
        String converted = GRADIENT.matcher(text).replaceAll("<gradient:#$1:#$3>$2</gradient>");
        converted = BRACED_HEX.matcher(converted).replaceAll("&#$1");
        Matcher matcher = LEGACY_CODE.matcher(converted);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(tag(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String tag(String code) {
        if (code.startsWith("#")) return "<reset><" + code + ">";
        if (code.length() > 1) return "<reset><#" + code.replaceAll("[x&§]", "") + ">";
        char lower = Character.toLowerCase(code.charAt(0));
        String name = TAGS.get(lower);
        return Character.digit(lower, 16) >= 0 ? "<reset><" + name + ">" : "<" + name + ">";
    }
}
