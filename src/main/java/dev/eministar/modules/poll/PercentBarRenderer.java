package dev.eministar.modules.poll;

public class PercentBarRenderer {
    private static final String FILLED = "▓";
    private static final String EMPTY = "░";
    private static final int BAR_LENGTH = 20;

    public static String render(double percent) {
        int filled = (int) Math.round((percent / 100.0) * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filled; i++) {
            bar.append(FILLED);
        }
        for (int i = filled; i < BAR_LENGTH; i++) {
            bar.append(EMPTY);
        }

        return bar.toString();
    }

    public static double calculatePercent(int votes, int total) {
        if (total == 0) return 0.0;
        return Math.round((votes * 100.0 / total) * 10.0) / 10.0;
    }

    public static String formatOptionLine(String label, int votes, int total, boolean showPercent) {
        StringBuilder line = new StringBuilder();
        line.append("**").append(label).append("**");

        if (showPercent) {
            double percent = calculatePercent(votes, total);
            line.append(" — ").append(String.format("%.1f%%", percent));
            line.append(" (").append(votes).append(" ").append(votes == 1 ? "Stimme" : "Stimmen").append(")");
            line.append("\n");
            line.append(render(percent));
        } else {
            line.append(" — *Verborgen bis zum Ende*");
        }

        return line.toString();
    }
}

