package dev.eministar.util;

public final class EmojiUtil {
    private EmojiUtil() {}

    // Wraps an emoji like 😄 into the requested form -> `😄` »
    public static String wrap(String emoji) {
        if (emoji == null || emoji.isEmpty()) return "";
        // If already wrapped (backticks and trailing »), return as-is
        if (emoji.startsWith("`") && emoji.endsWith("` » ")) return emoji;
        // Ensure we only include the emoji between backticks, then add a space and the guillemet
        return "`" + emoji + "` » ";
    }
}
