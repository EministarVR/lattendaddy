package dev.eministar.modules.suggestion;

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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SuggestionService {
    private static final Logger logger = LoggerFactory.getLogger(SuggestionService.class);
    private static final Path DATA_FILE = Paths.get("data/suggestions.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Map<String, Suggestion>> suggestions = new ConcurrentHashMap<>();
    private static int suggestionCounter = 1000;

    static {
        loadSuggestions();
    }

    private static void loadSuggestions() {
        try {
            if (!Files.exists(DATA_FILE)) {
                Files.createDirectories(DATA_FILE.getParent());
                saveSuggestions();
                return;
            }

            try (Reader reader = new InputStreamReader(Files.newInputStream(DATA_FILE), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, Map<String, Suggestion>>>(){}.getType();
                Map<String, Map<String, Suggestion>> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    suggestions.putAll(loaded);
                    // Find highest suggestion ID
                    suggestions.values().stream()
                            .flatMap(map -> map.values().stream())
                            .map(Suggestion::getSuggestionId)
                            .filter(id -> id.startsWith("SUG-"))
                            .map(id -> id.substring(4))
                            .mapToInt(Integer::parseInt)
                            .max()
                            .ifPresent(max -> suggestionCounter = max + 1);
                }
            }
            logger.info("Loaded {} guilds with suggestions", suggestions.size());
        } catch (Exception e) {
            logger.error("Failed to load suggestions", e);
        }
    }

    private static synchronized void saveSuggestions() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(DATA_FILE), StandardCharsets.UTF_8))) {
                gson.toJson(suggestions, writer);
            }
        } catch (Exception e) {
            logger.error("Failed to save suggestions", e);
        }
    }

    public static synchronized Suggestion createSuggestion(String guildId, String userId, String content) {
        String suggestionId = "SUG-" + String.format("%04d", suggestionCounter++);
        Suggestion suggestion = new Suggestion(suggestionId, guildId, userId, content);

        suggestions.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>())
                .put(suggestionId, suggestion);

        saveSuggestions();
        logger.info("Created suggestion {} in guild {}", suggestionId, guildId);
        return suggestion;
    }

    public static Optional<Suggestion> getSuggestion(String guildId, String suggestionId) {
        return Optional.ofNullable(suggestions.getOrDefault(guildId, Collections.emptyMap()).get(suggestionId));
    }

    public static Optional<Suggestion> getSuggestionByMessage(String guildId, String messageId) {
        Map<String, Suggestion> guildSuggestions = suggestions.get(guildId);
        if (guildSuggestions == null) return Optional.empty();

        return guildSuggestions.values().stream()
                .filter(s -> messageId.equals(s.getMessageId()))
                .findFirst();
    }

    public static List<Suggestion> getGuildSuggestions(String guildId) {
        return new ArrayList<>(suggestions.getOrDefault(guildId, Collections.emptyMap()).values());
    }

    public static List<Suggestion> getUserSuggestions(String guildId, String userId) {
        return suggestions.getOrDefault(guildId, Collections.emptyMap()).values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .sorted(Comparator.comparingLong(Suggestion::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public static List<Suggestion> getSuggestionsByStatus(String guildId, Suggestion.SuggestionStatus status) {
        return suggestions.getOrDefault(guildId, Collections.emptyMap()).values().stream()
                .filter(s -> s.getStatus() == status)
                .sorted(Comparator.comparingLong(Suggestion::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public static List<Suggestion> getTopSuggestions(String guildId, int limit) {
        return suggestions.getOrDefault(guildId, Collections.emptyMap()).values().stream()
                .filter(s -> s.getStatus() == Suggestion.SuggestionStatus.PENDING)
                .sorted(Comparator.comparingInt(Suggestion::getVoteScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static void updateSuggestion(Suggestion suggestion) {
        suggestions.computeIfAbsent(suggestion.getGuildId(), k -> new ConcurrentHashMap<>())
                .put(suggestion.getSuggestionId(), suggestion);
        saveSuggestions();
    }

    public static void deleteSuggestion(String guildId, String suggestionId) {
        Map<String, Suggestion> guildSuggestions = suggestions.get(guildId);
        if (guildSuggestions != null) {
            guildSuggestions.remove(suggestionId);
            saveSuggestions();
            logger.info("Deleted suggestion {} from guild {}", suggestionId, guildId);
        }
    }

    public static Map<Suggestion.SuggestionStatus, Long> getStatistics(String guildId) {
        return suggestions.getOrDefault(guildId, Collections.emptyMap()).values().stream()
                .collect(Collectors.groupingBy(Suggestion::getStatus, Collectors.counting()));
    }
}

