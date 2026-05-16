package com.referralplugin.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Text {

    private static final Pattern PATTERN = Pattern.compile("([^\\\\]?)&([0-9a-fk-or])");

    private static final Map<String, String> COLOR_CODES = new HashMap<>() {{
        put("0", "<reset><color:black>");
        put("1", "<reset><color:dark_blue>");
        put("2", "<reset><color:dark_green>");
        put("3", "<reset><color:dark_aqua>");
        put("4", "<reset><color:dark_red>");
        put("5", "<reset><color:dark_purple>");
        put("6", "<reset><color:gold>");
        put("7", "<reset><color:gray>");
        put("8", "<reset><color:dark_gray>");
        put("9", "<reset><color:blue>");
        put("a", "<reset><color:green>");
        put("b", "<reset><color:aqua>");
        put("c", "<reset><color:red>");
        put("d", "<reset><color:light_purple>");
        put("e", "<reset><color:yellow>");
        put("f", "<reset><color:white>");
        put("k", "<obfuscated>");
        put("l", "<bold>");
        put("m", "<strikethrough>");
        put("n", "<underlined>");
        put("o", "<italic>");
        put("r", "<reset>");
    }};

    private static final List<TagResolver> customResolvers = new ArrayList<>();
    private static MiniMessage MINI_MESSAGE;

    private static void initializeMiniMessage() {
        MINI_MESSAGE = MiniMessage.builder()
                .tags(TagResolver.builder()
                        .resolver(StandardTags.defaults())
                        .resolvers(customResolvers)
                        .build())
                .build();
    }

    public static void registerTagResolver(TagResolver resolver) {
        customResolvers.add(resolver);
        initializeMiniMessage();
    }

    static {
        initializeMiniMessage();
    }

    public static Component translate(String text) {
        if (text == null) return Component.empty();
        String processedText = replacePrimitiveWithMiniMessage(
                text.replaceAll(String.valueOf(LegacyComponentSerializer.SECTION_CHAR), "&"));
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(MINI_MESSAGE.deserialize(processedText));
    }

    public static Component[] translate(String... text) {
        return Arrays.stream(text).map(Text::translate).toArray(Component[]::new);
    }

    public static List<Component> translate(List<String> list) {
        List<Component> toReturn = new ArrayList<>();
        for (String text : list) {
            toReturn.add(translate(text.replaceAll(String.valueOf(LegacyComponentSerializer.SECTION_CHAR), "&")));
        }
        return toReturn;
    }

    private static String replacePrimitiveWithMiniMessage(String string) {
        return PATTERN.matcher(string).replaceAll(matchResult -> {
            String code = matchResult.group(2);
            String replacement = COLOR_CODES.get(code);
            if (replacement == null) return Matcher.quoteReplacement(matchResult.group(0));
            return Matcher.quoteReplacement(matchResult.group(1) + replacement);
        });
    }

    /**
     * Converts a Component to a legacy § color code string.
     * Works on both Java and Bedrock players via Geyser.
     */
    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    /**
     * Converts a raw & color code string directly to § color codes.
     * Fastest path — use when you don't need a Component at all.
     */
    public static String toLegacyString(String text) {
        if (text == null) return "";
        return toLegacy(translate(text));
    }

    public static String translateToPrimitive(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static List<String> translateToPrimitive(List<Component> components) {
        return components.stream().map(c -> translateToPrimitive(c)).toList();
    }

    public static String translateToMiniMessage(Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    public static List<String> translateToMiniMessage(List<Component> components) {
        return components.stream().map(c -> translateToMiniMessage(c)).toList();
    }

    public static String capitalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (String word : s.split(" +")) {
            sb.append(word.substring(0, 1).toUpperCase())
              .append(word.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }
}
