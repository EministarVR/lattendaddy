package dev.eministar.modules.suggestion;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SuggestionCommand implements Command {

    @Override
    public String name() {
        return "suggestion";
    }

    @Override
    public String description() {
        return "Verwaltung von Vorschlägen (Admin)";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getMessage().reply(EmojiUtil.wrap("ℹ️") + " Bitte nutze den Slash-Command `/suggestion`!").queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addOptions(
                        new OptionData(OptionType.STRING, "aktion", "Aktion", true)
                                .addChoice("Akzeptieren", "accept")
                                .addChoice("Ablehnen", "deny")
                                .addChoice("In Prüfung", "reviewing")
                                .addChoice("Umgesetzt", "implemented")
                                .addChoice("Warten", "pending")
                                .addChoice("Info", "info")
                                .addChoice("Statistik", "stats")
                                .addChoice("Top", "top"),
                        new OptionData(OptionType.STRING, "id", "Vorschlag-ID (z.B. SUG-1001)", false),
                        new OptionData(OptionType.STRING, "antwort", "Antwort an den User (optional)", false)
                );
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieser Command kann nur auf einem Server verwendet werden!")
                    .setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst die **Server verwalten** Berechtigung!")
                    .setEphemeral(true).queue();
            return;
        }

        var actionOption = event.getOption("aktion");
        if (actionOption == null) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler: Keine Aktion angegeben!").setEphemeral(true).queue();
            return;
        }

        String action = actionOption.getAsString();

        switch (action) {
            case "accept" -> handleStatusChange(event, Suggestion.SuggestionStatus.ACCEPTED);
            case "deny" -> handleStatusChange(event, Suggestion.SuggestionStatus.DENIED);
            case "reviewing" -> handleStatusChange(event, Suggestion.SuggestionStatus.REVIEWING);
            case "implemented" -> handleStatusChange(event, Suggestion.SuggestionStatus.IMPLEMENTED);
            case "pending" -> handleStatusChange(event, Suggestion.SuggestionStatus.PENDING);
            case "info" -> handleInfo(event);
            case "stats" -> handleStats(event);
            case "top" -> handleTop(event);
            default -> event.reply(EmojiUtil.wrap("❌") + " Unbekannte Aktion!").setEphemeral(true).queue();
        }
    }

    private void handleStatusChange(SlashCommandInteraction event, Suggestion.SuggestionStatus newStatus) {
        var idOption = event.getOption("id");
        if (idOption == null) {
            event.reply(EmojiUtil.wrap("❌") + " Bitte gib eine Vorschlag-ID an!")
                    .setEphemeral(true).queue();
            return;
        }

        String suggestionId = idOption.getAsString().toUpperCase();
        if (!suggestionId.startsWith("SUG-")) {
            suggestionId = "SUG-" + suggestionId;
        }

        Optional<Suggestion> optSuggestion = SuggestionService.getSuggestion(
                event.getGuild().getId(),
                suggestionId
        );

        if (optSuggestion.isEmpty()) {
            event.reply(EmojiUtil.wrap("❌") + " Vorschlag `" + suggestionId + "` nicht gefunden!")
                    .setEphemeral(true).queue();
            return;
        }

        Suggestion suggestion = optSuggestion.get();
        Suggestion.SuggestionStatus oldStatus = suggestion.getStatus();

        // Update status
        suggestion.setStatus(newStatus);
        suggestion.setHandledBy(event.getUser().getId());

        // Update admin response if provided
        var responseOption = event.getOption("antwort");
        if (responseOption != null) {
            suggestion.setAdminResponse(responseOption.getAsString());
        }

        SuggestionService.updateSuggestion(suggestion);

        // Update message in suggestion channel
        String suggestionChannelId = Config.getSuggestionChannelId();
        if (!suggestionChannelId.isEmpty()) {
            TextChannel suggestionChannel = event.getGuild().getTextChannelById(suggestionChannelId);
            if (suggestionChannel != null) {
                SuggestionListener.updateSuggestionMessage(suggestionChannel, suggestion);
            }
        }

        // Send confirmation
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("✅") + " Status aktualisiert!");
        embed.setDescription(
                "**Vorschlag:** `" + suggestion.getSuggestionId() + "`\n" +
                "**Status:** " + oldStatus.getFormatted() + " → " + newStatus.getFormatted() + "\n\n" +
                (suggestion.getAdminResponse() != null && !suggestion.getAdminResponse().isEmpty()
                    ? "**Antwort:** " + suggestion.getAdminResponse() + "\n\n"
                    : "") +
                EmojiUtil.wrap("💡") + " *Der User wurde benachrichtigt.*"
        );
        embed.setColor(new Color(newStatus.getColor()));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();

        // Notify user via DM
        notifyUser(event, suggestion, newStatus);
    }

    private void notifyUser(SlashCommandInteraction event, Suggestion suggestion, Suggestion.SuggestionStatus newStatus) {
        User user = event.getJDA().getUserById(suggestion.getUserId());
        if (user == null) return;

        user.openPrivateChannel().queue(dm -> {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(newStatus.getEmoji() + " Vorschlag-Update!");

            StringBuilder description = new StringBuilder();
            description.append("Der Status deines Vorschlags hat sich geändert!\n\n");
            description.append("**Vorschlag-ID:** `").append(suggestion.getSuggestionId()).append("`\n");
            description.append("**Neuer Status:** ").append(newStatus.getFormatted()).append("\n\n");

            if (suggestion.getAdminResponse() != null && !suggestion.getAdminResponse().isEmpty()) {
                description.append(EmojiUtil.wrap("💬")).append(" **Team-Antwort:**\n");
                description.append("*").append(suggestion.getAdminResponse()).append("*\n\n");
            }

            description.append("**Dein Vorschlag:**\n```\n");
            description.append(suggestion.getContent().length() > 200
                    ? suggestion.getContent().substring(0, 200) + "..."
                    : suggestion.getContent());
            description.append("\n```\n\n");

            description.append(EmojiUtil.wrap("📊")).append(" **Voting:** ")
                    .append(suggestion.getUpvotes()).append(" 👍 · ")
                    .append(suggestion.getDownvotes()).append(" 👎");

            embed.setDescription(description.toString());
            embed.setColor(new Color(newStatus.getColor()));
            embed.setFooter("Behandelt von " + event.getUser().getName(), event.getUser().getAvatarUrl());
            embed.setTimestamp(Instant.now());

            dm.sendMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleInfo(SlashCommandInteraction event) {
        var idOption = event.getOption("id");
        if (idOption == null) {
            event.reply(EmojiUtil.wrap("❌") + " Bitte gib eine Vorschlag-ID an!")
                    .setEphemeral(true).queue();
            return;
        }

        String suggestionId = idOption.getAsString().toUpperCase();
        if (!suggestionId.startsWith("SUG-")) {
            suggestionId = "SUG-" + suggestionId;
        }

        Optional<Suggestion> optSuggestion = SuggestionService.getSuggestion(
                event.getGuild().getId(),
                suggestionId
        );

        if (optSuggestion.isEmpty()) {
            event.reply(EmojiUtil.wrap("❌") + " Vorschlag nicht gefunden!")
                    .setEphemeral(true).queue();
            return;
        }

        Suggestion suggestion = optSuggestion.get();
        User author = event.getJDA().getUserById(suggestion.getUserId());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("📋") + " Vorschlag-Details");
        embed.addField("ID", "`" + suggestion.getSuggestionId() + "`", true);
        embed.addField("Status", suggestion.getStatus().getFormatted(), true);
        embed.addField("Autor", author != null ? author.getAsMention() : "Unbekannt", true);
        embed.addField("Erstellt", "<t:" + (suggestion.getCreatedAt() / 1000) + ":R>", true);
        embed.addField("Upvotes", "👍 " + suggestion.getUpvotes(), true);
        embed.addField("Downvotes", "👎 " + suggestion.getDownvotes(), true);
        embed.addField("Score", (suggestion.getVoteScore() > 0 ? "+" : "") + suggestion.getVoteScore(), true);

        if (suggestion.getUpvotes() + suggestion.getDownvotes() > 0) {
            embed.addField("Zustimmung", String.format("%.1f%%", suggestion.getApprovalRate()), true);
        }

        embed.addField("Inhalt", "```\n" + suggestion.getContent() + "\n```", false);

        if (suggestion.getAdminResponse() != null && !suggestion.getAdminResponse().isEmpty()) {
            embed.addField("Team-Antwort", "*" + suggestion.getAdminResponse() + "*", false);
        }

        if (suggestion.getHandledBy() != null) {
            User handler = event.getJDA().getUserById(suggestion.getHandledBy());
            embed.addField("Behandelt von", handler != null ? handler.getAsMention() : "Unbekannt", true);
        }

        embed.setColor(new Color(suggestion.getStatus().getColor()));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleStats(SlashCommandInteraction event) {
        var stats = SuggestionService.getStatistics(event.getGuild().getId());

        if (stats.isEmpty()) {
            event.reply(EmojiUtil.wrap("📊") + " Noch keine Vorschläge vorhanden!")
                    .setEphemeral(true).queue();
            return;
        }

        long total = stats.values().stream().mapToLong(Long::longValue).sum();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("📊") + " Vorschläge-Statistik");

        StringBuilder description = new StringBuilder();
        description.append("**Gesamt:** ").append(total).append(" Vorschläge\n\n");

        for (Suggestion.SuggestionStatus status : Suggestion.SuggestionStatus.values()) {
            long count = stats.getOrDefault(status, 0L);
            if (count > 0) {
                double percentage = (count * 100.0) / total;
                description.append(status.getEmoji()).append(" **").append(status.getDisplayName())
                        .append(":** ").append(count)
                        .append(" (").append(String.format("%.1f", percentage)).append("%)\n");
            }
        }

        // Top suggestions
        List<Suggestion> topSuggestions = SuggestionService.getTopSuggestions(event.getGuild().getId(), 3);
        if (!topSuggestions.isEmpty()) {
            description.append("\n").append(EmojiUtil.wrap("🏆")).append(" **Top Vorschläge:**\n");
            for (int i = 0; i < topSuggestions.size(); i++) {
                Suggestion s = topSuggestions.get(i);
                description.append((i + 1)).append(". `").append(s.getSuggestionId()).append("` - ")
                        .append("Score: ").append(s.getVoteScore())
                        .append(" (👍").append(s.getUpvotes()).append(" 👎").append(s.getDownvotes()).append(")\n");
            }
        }

        embed.setDescription(description.toString());
        embed.setColor(new Color(0x5865F2));
        embed.setTimestamp(Instant.now());
        embed.setFooter("Serverstatistiken", event.getGuild().getIconUrl());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleTop(SlashCommandInteraction event) {
        List<Suggestion> topSuggestions = SuggestionService.getTopSuggestions(event.getGuild().getId(), 10);

        if (topSuggestions.isEmpty()) {
            event.reply(EmojiUtil.wrap("📊") + " Noch keine ausstehenden Vorschläge vorhanden!")
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🏆") + " Top Vorschläge");

        StringBuilder description = new StringBuilder();
        description.append("Die beliebtesten ausstehenden Vorschläge:\n\n");

        for (int i = 0; i < topSuggestions.size(); i++) {
            Suggestion s = topSuggestions.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : (i + 1) + ".";

            description.append(medal).append(" **").append(s.getSuggestionId()).append("**\n");
            description.append("   Score: **").append(s.getVoteScore()).append("** ")
                    .append("(👍").append(s.getUpvotes()).append(" 👎").append(s.getDownvotes()).append(") ")
                    .append("· ").append(String.format("%.0f%%", s.getApprovalRate())).append(" Zustimmung\n");

            String preview = s.getContent().length() > 50
                    ? s.getContent().substring(0, 50) + "..."
                    : s.getContent();
            description.append("   *").append(preview).append("*\n\n");
        }

        embed.setDescription(description.toString());
        embed.setColor(new Color(0xFEE75C));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}

