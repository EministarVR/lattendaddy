package dev.eministar.modules.birthday;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.eministar.util.EmojiUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Simple persistent store for birthdays per guild. Data format:
 * {
 *   "<guildId>": {
 *     "listMessageId": "<messageId>",
 *     "birthdays": {
 *        "<userId>": { "day": 1, "month": 1, "year": 1990, "lastCongratsId": "..." }
 *     }
 *   }
 * }
 */
public class BirthdayService {
    private static final Path DATA_PATH = Paths.get("data/birthdays.json");
    private static JsonObject root;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        load();
    }

    private static void load() {
        try {
            if (!Files.exists(DATA_PATH)) {
                Files.createDirectories(DATA_PATH.getParent());
                root = new JsonObject();
                save();
                return;
            }

            try (Reader r = Files.newBufferedReader(DATA_PATH, StandardCharsets.UTF_8)) {
                root = GSON.fromJson(r, JsonObject.class);
                if (root == null) root = new JsonObject();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load birthdays.json", e);
        }
    }

    private static void save() {
        try (Writer w = Files.newBufferedWriter(DATA_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save birthdays.json", e);
        }
    }

    private static JsonObject ensureGuild(String guildId) {
        if (!root.has(guildId) || root.get(guildId).isJsonNull()) {
            JsonObject node = new JsonObject();
            node.add("birthdays", new JsonObject());
            node.addProperty("listMessageId", "");
            root.add(guildId, node);
            save();
        }
        return root.getAsJsonObject(guildId);
    }

    public static void setListMessageId(String guildId, String messageId) {
        JsonObject guild = ensureGuild(guildId);
        guild.addProperty("listMessageId", messageId == null ? "" : messageId);
        save();
    }

    public static String getListMessageId(String guildId) {
        JsonObject guild = ensureGuild(guildId);
        return guild.has("listMessageId") ? guild.get("listMessageId").getAsString() : "";
    }

    public static void setBirthday(String guildId, String userId, int day, int month, Integer year) {
        JsonObject guild = ensureGuild(guildId);
        JsonObject birthdays = guild.getAsJsonObject("birthdays");
        JsonObject b = new JsonObject();
        b.addProperty("day", day);
        b.addProperty("month", month);
        if (year != null) b.addProperty("year", year);
        b.addProperty("lastCongratsId", "");
        birthdays.add(userId, b);
        save();
    }

    public static Optional<BirthdayEntry> getBirthday(String guildId, String userId) {
        JsonObject guild = ensureGuild(guildId);
        JsonObject birthdays = guild.getAsJsonObject("birthdays");
        if (!birthdays.has(userId)) return Optional.empty();
        JsonObject b = birthdays.getAsJsonObject(userId);
        Integer year = b.has("year") ? b.get("year").getAsInt() : null;
        int day = b.get("day").getAsInt();
        int month = b.get("month").getAsInt();
        String last = b.has("lastCongratsId") ? b.get("lastCongratsId").getAsString() : "";
        String lastDate = b.has("lastCongratsDate") ? b.get("lastCongratsDate").getAsString() : "";
        return Optional.of(new BirthdayEntry(day, month, year, last, lastDate));
    }

    public static Map<String, BirthdayEntry> getAllBirthdays(String guildId) {
        JsonObject guild = ensureGuild(guildId);
        JsonObject birthdays = guild.getAsJsonObject("birthdays");
        Map<String, BirthdayEntry> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : birthdays.entrySet()) {
            String userId = e.getKey();
            JsonObject b = e.getValue().getAsJsonObject();
            Integer year = b.has("year") ? b.get("year").getAsInt() : null;
            int day = b.get("day").getAsInt();
            int month = b.get("month").getAsInt();
            String last = b.has("lastCongratsId") ? b.get("lastCongratsId").getAsString() : "";
            String lastDate = b.has("lastCongratsDate") ? b.get("lastCongratsDate").getAsString() : "";
            map.put(userId, new BirthdayEntry(day, month, year, last, lastDate));
        }
        return map;
    }

    public static void setLastCongratsId(String guildId, String userId, String messageId) {
        JsonObject guild = ensureGuild(guildId);
        JsonObject birthdays = guild.getAsJsonObject("birthdays");
        if (!birthdays.has(userId)) return;
        JsonObject b = birthdays.getAsJsonObject(userId);
        b.addProperty("lastCongratsId", messageId == null ? "" : messageId);
        save();
    }

    // New: store last congrats date (ISO yyyy-MM-dd) together with message id
    public static void setLastCongrats(String guildId, String userId, String messageId, String isoDate) {
        JsonObject guild = ensureGuild(guildId);
        JsonObject birthdays = guild.getAsJsonObject("birthdays");
        if (!birthdays.has(userId)) return;
        JsonObject b = birthdays.getAsJsonObject(userId);
        b.addProperty("lastCongratsId", messageId == null ? "" : messageId);
        b.addProperty("lastCongratsDate", isoDate == null ? "" : isoDate);
        save();
    }

    public static void removeBirthday(String guildId, String userId) {
        JsonObject guild = ensureGuild(guildId);
        JsonObject birthdays = guild.getAsJsonObject("birthdays");
        if (birthdays.has(userId)) {
            birthdays.remove(userId);
            save();
        }
    }

    public static class BirthdayEntry {
        public final int day;
        public final int month;
        public final Integer year;
        public final String lastCongratsId;
        public final String lastCongratsDate;

        public BirthdayEntry(int day, int month, Integer year, String lastCongratsId) {
            this.day = day;
            this.month = month;
            this.year = year;
            this.lastCongratsId = lastCongratsId;
            this.lastCongratsDate = "";
        }

        public BirthdayEntry(int day, int month, Integer year, String lastCongratsId, String lastCongratsDate) {
            this.day = day;
            this.month = month;
            this.year = year;
            this.lastCongratsId = lastCongratsId;
            this.lastCongratsDate = lastCongratsDate == null ? "" : lastCongratsDate;
        }

        public String pretty() {
            if (year != null) return String.format("%02d.%02d.%04d", day, month, year);
            return String.format("%02d.%02d", day, month);
        }
    }
}
