package dev.eministar.modules.ticket;

import com.google.gson.annotations.SerializedName;

public class Ticket {
    @SerializedName("ticketId")
    private final String ticketId;

    @SerializedName("userId")
    private final String userId;

    @SerializedName("guildId")
    private final String guildId;

    @SerializedName("channelId")
    private String channelId;

    @SerializedName("category")
    private final TicketCategory category;

    @SerializedName("reason")
    private String reason;

    @SerializedName("claimedBy")
    private String claimedBy;

    @SerializedName("createdAt")
    private final long createdAt;

    @SerializedName("closedAt")
    private long closedAt;

    @SerializedName("status")
    private TicketStatus status;

    public Ticket(String ticketId, String userId, String guildId, TicketCategory category) {
        this.ticketId = ticketId;
        this.userId = userId;
        this.guildId = guildId;
        this.category = category;
        this.createdAt = System.currentTimeMillis();
        this.status = TicketStatus.OPEN;
    }

    // Getters and Setters
    public String getTicketId() { return ticketId; }
    public String getUserId() { return userId; }
    public String getGuildId() { return guildId; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public TicketCategory getCategory() { return category; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public long getCreatedAt() { return createdAt; }
    public long getClosedAt() { return closedAt; }
    public void setClosedAt(long closedAt) { this.closedAt = closedAt; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }

    public enum TicketStatus {
        OPEN, CLAIMED, CLOSED
    }
}

