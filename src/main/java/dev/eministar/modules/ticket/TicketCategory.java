package dev.eministar.modules.ticket;

public enum TicketCategory {
    SUPPORT("Support", "🎫", "Benötigst du Hilfe? Wir sind für dich da!"),
    BEWERBUNG("Bewerbung", "📝", "Bewirb dich bei unserem Team!"),
    REPORT("Report", "⚠️", "Melde Regelverstöße oder Probleme"),
    EVENT("Event", "🎉", "Hast du eine Event-Idee oder Frage?");

    private final String displayName;
    private final String emoji;
    private final String description;

    TicketCategory(String displayName, String emoji, String description) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getDescription() {
        return description;
    }

    public String getFormattedName() {
        return emoji + " " + displayName;
    }
}
