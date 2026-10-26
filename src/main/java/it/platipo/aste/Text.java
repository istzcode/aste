package it.platipo.aste;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing testi: codici legacy (&a, &#RRGGBB) + MiniMessage. */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEG = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private Text() {}

    public static String legacyToMini(String in) {
        String s = in.replace('§', '&');
        s = HEX.matcher(s).replaceAll("<#$1>");
        Matcher m = LEG.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(tag(Character.toLowerCase(m.group(1).charAt(0)))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String tag(char c) {
        return switch (c) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            default -> "<reset>";
        };
    }

    public static Component parse(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        return MM.deserialize(legacyToMini(s));
    }

    /** Come parse(), ma senza corsivo di default (per nomi/lore item). */
    public static Component item(String s) {
        return parse(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String replace(String s, Map<String, String> ph) {
        if (s == null) return "";
        for (Map.Entry<String, String> e : ph.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return s;
    }
}
