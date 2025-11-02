package dev.eministar.modules.poll;

import java.time.Instant;
import java.util.*;

public class PollData {
    public String id;
    public String guildId;
    public String channelId;
    public String messageId;
    public String threadId;
    public String creatorId;
    public String title;
    public String description;
    public List<PollOption> options;
    public MultiConfig multi;
    public boolean anonymous;
    public boolean allowVoteChange;
    public List<String> allowedVoterRoles;
    public QuorumConfig quorum;
    public String visibility; // live, final
    public String startedAt;
    public String endsAt;
    public String status; // open, closed, archived
    public Map<String, List<String>> votes; // userId -> [optionIds]
    public Map<String, Integer> totals; // optionId -> count
    public String lastEditAt;

    public PollData() {
        this.options = new ArrayList<>();
        this.votes = new HashMap<>();
        this.totals = new HashMap<>();
        this.allowedVoterRoles = new ArrayList<>();
        this.multi = new MultiConfig();
        this.quorum = new QuorumConfig();
    }

    public static class PollOption {
        public String id;
        public String label;

        public PollOption(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static class MultiConfig {
        public boolean enabled = false;
        public int maxChoices = 1;
    }

    public static class QuorumConfig {
        public boolean enabled = false;
        public int minVotes = 0;
    }

    public boolean isOpen() {
        return "open".equals(status);
    }

    public boolean isClosed() {
        return "closed".equals(status);
    }

    public Instant getEndsAtInstant() {
        return Instant.parse(endsAt);
    }

    public Instant getStartedAtInstant() {
        return Instant.parse(startedAt);
    }

    public int getTotalVotes() {
        return totals.values().stream().mapToInt(Integer::intValue).sum();
    }

    public PollOption getOptionById(String id) {
        return options.stream().filter(o -> o.id.equals(id)).findFirst().orElse(null);
    }
}

