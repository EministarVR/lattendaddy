package dev.eministar.modules.suggestion;

import com.google.gson.annotations.SerializedName;

public class Suggestion {
    @SerializedName("id")
    private String suggestionId;

    @SerializedName("guild_id")
    private String guildId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("content")
    private String content;

    @SerializedName("message_id")
    private String messageId;

    @SerializedName("thread_id")
    private String threadId;

    @SerializedName("status")
    private SuggestionStatus status;

    @SerializedName("created_at")
    private long createdAt;

    @SerializedName("updated_at")
    private long updatedAt;

    @SerializedName("handled_by")
    private String handledBy;

    @SerializedName("admin_response")
    private String adminResponse;

    @SerializedName("upvotes")
    private int upvotes;

    @SerializedName("downvotes")
    private int downvotes;

    public enum SuggestionStatus {
        PENDING("⏳", "Ausstehend", 0xFEE75C),
        ACCEPTED("✅", "Akzeptiert", 0x57F287),
        DENIED("❌", "Abgelehnt", 0xED4245),
        IMPLEMENTED("🎉", "Umgesetzt", 0x5865F2),
        REVIEWING("🔍", "In Prüfung", 0xEB459E);

        private final String emoji;
        private final String displayName;
        private final int color;

        SuggestionStatus(String emoji, String displayName, int color) {
            this.emoji = emoji;
            this.displayName = displayName;
            this.color = color;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }

        public String getFormatted() {
            return emoji + " **" + displayName + "**";
        }
    }

    // Constructors
    public Suggestion() {
        this.status = SuggestionStatus.PENDING;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.upvotes = 0;
        this.downvotes = 0;
    }

    public Suggestion(String suggestionId, String guildId, String userId, String content) {
        this();
        this.suggestionId = suggestionId;
        this.guildId = guildId;
        this.userId = userId;
        this.content = content;
    }

    // Getters and Setters
    public String getSuggestionId() {
        return suggestionId;
    }

    public void setSuggestionId(String suggestionId) {
        this.suggestionId = suggestionId;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public void setStatus(SuggestionStatus status) {
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public String getAdminResponse() {
        return adminResponse;
    }

    public void setAdminResponse(String adminResponse) {
        this.adminResponse = adminResponse;
    }

    public int getUpvotes() {
        return upvotes;
    }

    public void setUpvotes(int upvotes) {
        this.upvotes = upvotes;
    }

    public int getDownvotes() {
        return downvotes;
    }

    public void setDownvotes(int downvotes) {
        this.downvotes = downvotes;
    }

    public int getVoteScore() {
        return upvotes - downvotes;
    }

    public double getApprovalRate() {
        int total = upvotes + downvotes;
        if (total == 0) return 0.0;
        return (double) upvotes / total * 100.0;
    }
}

