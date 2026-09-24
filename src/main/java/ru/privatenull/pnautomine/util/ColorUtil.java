package ru.privatenull.pnautomine.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight color utility replacing the old pnLibrary dependency. */
public final class ColorUtil {
    private static final Pattern HEX = Pattern.compile("(?i)#([0-9a-f]{6})");
    private ColorUtil() {}

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher matcher = HEX.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) replacement.append('§').append(c);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(out);
        return out.toString().replace('&', '§');
    }
}
