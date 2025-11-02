package dev.eministar.modules.poll;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PollStore {
    private final String filePath;
    private final Gson gson;
    private final Map<String, PollData> polls;
    private int sequence;

    public PollStore(String filePath) {
        this.filePath = filePath;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.polls = new ConcurrentHashMap<>();
        this.sequence = 0;
        load();
    }

    public synchronized void load() {
        File file = new File(filePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            save();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<StoreData>() {}.getType();
            StoreData data = gson.fromJson(reader, type);
            if (data != null) {
                this.sequence = data.seq;
                if (data.polls != null) {
                    this.polls.putAll(data.polls);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load polls: " + e.getMessage());
        }
    }

    public synchronized void save() {
        try (Writer writer = new FileWriter(filePath)) {
            StoreData data = new StoreData();
            data.seq = sequence;
            data.polls = new HashMap<>(polls);
            gson.toJson(data, writer);
        } catch (Exception e) {
            System.err.println("Failed to save polls: " + e.getMessage());
        }
    }

    public String generateId() {
        sequence++;
        save();
        return String.format("P-%s-%03d",
                Instant.now().toString().substring(0, 10).replace("-", ""),
                sequence % 1000);
    }

    public void put(String key, PollData data) {
        polls.put(key, data);
        save();
    }

    public PollData get(String key) {
        return polls.get(key);
    }

    public void remove(String key) {
        polls.remove(key);
        save();
    }

    public Map<String, PollData> getAll() {
        return new HashMap<>(polls);
    }

    public Map<String, PollData> getByGuild(String guildId) {
        Map<String, PollData> result = new HashMap<>();
        polls.forEach((key, data) -> {
            if (data.guildId.equals(guildId)) {
                result.put(key, data);
            }
        });
        return result;
    }

    private static class StoreData {
        int seq;
        Map<String, PollData> polls;
    }
}

