package dev.eministar.modules.counting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CountingGame {
    private static final Logger logger = LoggerFactory.getLogger(CountingGame.class);
    private static final Path DATA_FILE = Paths.get("data/counting.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // channelId -> GameState
    private static final Map<String, GameState> games = new ConcurrentHashMap<>();

    static {
        loadGames();
    }

    public static class GameState {
        private int currentNumber;
        private String lastUserId;
        private int highscore;
        private int totalCounts;
        private int totalFails;

        public GameState() {
            this.currentNumber = 1;
            this.lastUserId = null;
            this.highscore = 0;
            this.totalCounts = 0;
            this.totalFails = 0;
        }

        public int getCurrentNumber() {
            return currentNumber;
        }

        public void setCurrentNumber(int currentNumber) {
            this.currentNumber = currentNumber;
        }

        public String getLastUserId() {
            return lastUserId;
        }

        public void setLastUserId(String lastUserId) {
            this.lastUserId = lastUserId;
        }

        public int getHighscore() {
            return highscore;
        }

        public void setHighscore(int highscore) {
            this.highscore = highscore;
        }

        public int getTotalCounts() {
            return totalCounts;
        }

        public void incrementTotalCounts() {
            this.totalCounts++;
        }

        public int getTotalFails() {
            return totalFails;
        }

        public void incrementTotalFails() {
            this.totalFails++;
        }

        public void reset() {
            if (currentNumber - 1 > highscore) {
                highscore = currentNumber - 1;
            }
            currentNumber = 1;
            lastUserId = null;
        }
    }

    private static void loadGames() {
        try {
            if (!Files.exists(DATA_FILE)) {
                Files.createDirectories(DATA_FILE.getParent());
                saveGames();
                return;
            }

            try (Reader reader = new InputStreamReader(Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, GameState>>(){}.getType();
                Map<String, GameState> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    games.putAll(loaded);
                }
            }
            logger.info("Loaded counting games for {} channels", games.size());
        } catch (Exception e) {
            logger.error("Failed to load counting games", e);
        }
    }

    private static void saveGames() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(DATA_FILE), StandardCharsets.UTF_8))) {
                gson.toJson(games, writer);
            }
        } catch (Exception e) {
            logger.error("Failed to save counting games", e);
        }
    }

    public static GameState getOrCreateGame(String channelId) {
        return games.computeIfAbsent(channelId, k -> new GameState());
    }

    public static void updateGame(String channelId, GameState state) {
        games.put(channelId, state);
        saveGames();
    }

    public static boolean isValidNumber(String input) {
        input = input.trim();

        // Prüfe auf einfache Zahl
        if (input.matches("\\d+")) {
            return true;
        }

        // Prüfe auf mathematischen Ausdruck (z.B. 5+3, 10-2, 4*2, 8/2)
        if (input.matches("\\d+\\s*[+\\-*/]\\s*\\d+")) {
            return true;
        }

        return false;
    }

    public static Integer evaluateExpression(String input) {
        try {
            input = input.trim().replaceAll("\\s+", "");

            // Einfache Zahl
            if (input.matches("\\d+")) {
                return Integer.parseInt(input);
            }

            // Addition
            if (input.contains("+")) {
                String[] parts = input.split("\\+");
                if (parts.length == 2) {
                    return Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]);
                }
            }

            // Subtraktion
            if (input.contains("-") && !input.startsWith("-")) {
                String[] parts = input.split("-");
                if (parts.length == 2) {
                    return Integer.parseInt(parts[0]) - Integer.parseInt(parts[1]);
                }
            }

            // Multiplikation
            if (input.contains("*")) {
                String[] parts = input.split("\\*");
                if (parts.length == 2) {
                    return Integer.parseInt(parts[0]) * Integer.parseInt(parts[1]);
                }
            }

            // Division
            if (input.contains("/")) {
                String[] parts = input.split("/");
                if (parts.length == 2) {
                    int divisor = Integer.parseInt(parts[1]);
                    if (divisor == 0) return null;
                    return Integer.parseInt(parts[0]) / Integer.parseInt(parts[1]);
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}

