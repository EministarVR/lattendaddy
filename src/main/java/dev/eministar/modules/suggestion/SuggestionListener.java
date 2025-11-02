package dev.eministar.modules.suggestion;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

public class SuggestionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SuggestionListener.class);
    private static final String UPVOTE_EMOJI = "👍";
    private static final String DOWNVOTE_EMOJI = "👎";

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots
        if (event.getAuthor().isBot()) return;
        if (event.getGuild() == null) return;

        String suggestionChannelId = Config.getSuggestionChannelId();
        if (suggestionChannelId.isEmpty()) return;

        // Check if message is in suggestion channel
        if (!event.getChannel().getId().equals(suggestionChannelId)) return;

        // Delete original message
        String content = event.getMessage().getContentRaw();
        User author = event.getAuthor();

        event.getMessage().delete().queue(
                success -> createSuggestionEmbed(event.getChannel().asTextChannel(), author, content),
                error -> logger.error("Failed to delete suggestion message", error)
        );
    }

    private void createSuggestionEmbed(TextChannel channel, User author, String content) {
        // Validate content length
        if (content.length() < 10) {
            channel.sendMessage(author.getAsMention() + " " + EmojiUtil.wrap("❌") +
                    " Dein Vorschlag ist zu kurz! Bitte schreibe mindestens 10 Zeichen.")
                    .queue(msg -> {
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000);
                                msg.delete().queue();
                            } catch (Exception e) {
                                // Ignore
                            }
                        }).start();
                    });
            return;
        }

        if (content.length() > 1500) {
            channel.sendMessage(author.getAsMention() + " " + EmojiUtil.wrap("❌") +
                    " Dein Vorschlag ist zu lang! Maximal 1500 Zeichen erlaubt.")
                    .queue(msg -> {
                        new Thread(() -> {
                            try {
                                Thread.sleep(5000);
                                msg.delete().queue();
                            } catch (Exception e) {
                                // Ignore
                            }
                        }).start();
                    });
            return;
        }

        // Create suggestion
        Suggestion suggestion = SuggestionService.createSuggestion(
                channel.getGuild().getId(),
                author.getId(),
                content
        );

        // Create beautiful embed
        EmbedBuilder embed = createSuggestionEmbed(suggestion, author);

        // Send embed
        channel.sendMessageEmbeds(embed.build()).queue(message -> {
            // Save message ID
            suggestion.setMessageId(message.getId());
            SuggestionService.updateSuggestion(suggestion);

            // Add reactions
            message.addReaction(Emoji.fromUnicode(UPVOTE_EMOJI)).queue();
            message.addReaction(Emoji.fromUnicode(DOWNVOTE_EMOJI)).queue();

            // Create thread
            createCommentThread(message, suggestion, author);

            // Send confirmation DM
            sendConfirmationDM(author, suggestion, channel);
        }, error -> logger.error("Failed to send suggestion embed", error));
    }

    private EmbedBuilder createSuggestionEmbed(Suggestion suggestion, User author) {
        EmbedBuilder embed = new EmbedBuilder();

        // Title with ID and Status
        embed.setTitle(
                EmojiUtil.wrap("💡") + " Vorschlag " +
                EmojiUtil.wrap("·") + " " + suggestion.getSuggestionId()
        );

        // Main content
        StringBuilder description = new StringBuilder();
        description.append("**Vorschlag:**\n");
        description.append("```\n").append(suggestion.getContent()).append("\n```\n\n");

        // Status
        description.append(EmojiUtil.wrap("📊") + " **Status:** ");
        description.append(suggestion.getStatus().getFormatted()).append("\n\n");

        // Votes
        int upvotes = suggestion.getUpvotes();
        int downvotes = suggestion.getDownvotes();
        int score = suggestion.getVoteScore();
        double approval = suggestion.getApprovalRate();

        description.append(EmojiUtil.wrap("📈") + " **Voting:**\n");
        description.append(UPVOTE_EMOJI + " **").append(upvotes).append("** Upvotes");
        description.append(" " + EmojiUtil.wrap("·") + " ");
        description.append(DOWNVOTE_EMOJI + " **").append(downvotes).append("** Downvotes\n");

        if (upvotes + downvotes > 0) {
            description.append(EmojiUtil.wrap("📊") + " Score: **").append(score > 0 ? "+" : "").append(score).append("**");
            description.append(" " + EmojiUtil.wrap("·") + " ");
            description.append("Zustimmung: **").append(String.format("%.1f", approval)).append("%**\n");
        }

        // Admin response if exists
        if (suggestion.getAdminResponse() != null && !suggestion.getAdminResponse().isEmpty()) {
            description.append("\n").append(EmojiUtil.wrap("💬") + " **Team-Antwort:**\n");
            description.append("*").append(suggestion.getAdminResponse()).append("*");
        }

        embed.setDescription(description.toString());

        // Color based on status
        embed.setColor(new Color(suggestion.getStatus().getColor()));

        // Author info
        embed.setAuthor(
                author.getName(),
                null,
                author.getAvatarUrl()
        );

        // Footer with info
        embed.setFooter(
                "Stimme mit " + UPVOTE_EMOJI + " oder " + DOWNVOTE_EMOJI + " ab · Nutze den Thread für Kommentare",
                null
        );

        embed.setTimestamp(Instant.ofEpochMilli(suggestion.getCreatedAt()));

        return embed;
    }

    private void createCommentThread(Message message, Suggestion suggestion, User author) {
        String threadName = EmojiUtil.wrap("💬") + " Kommentare · " + suggestion.getSuggestionId();

        message.createThreadChannel(threadName).queue(thread -> {
            suggestion.setThreadId(thread.getId());
            SuggestionService.updateSuggestion(suggestion);

            // Send welcome message to thread
            EmbedBuilder welcomeEmbed = new EmbedBuilder();
            welcomeEmbed.setDescription(
                    EmojiUtil.wrap("💬") + " **Diskussions-Thread**\n\n" +
                    "Hier kannst du mit anderen über diesen Vorschlag diskutieren!\n\n" +
                    EmojiUtil.wrap("📌") + " **Regeln:**\n" +
                    "• Bleibe respektvoll\n" +
                    "• Konstruktive Kritik ist willkommen\n" +
                    "• Spam wird entfernt\n\n" +
                    EmojiUtil.wrap("💡") + " *Vorschlag von " + author.getAsMention() + "*"
            );
            welcomeEmbed.setColor(new Color(0x5865F2));

            thread.sendMessageEmbeds(welcomeEmbed.build()).queue();
        }, error -> logger.error("Failed to create thread for suggestion {}", suggestion.getSuggestionId(), error));
    }

    private void sendConfirmationDM(User user, Suggestion suggestion, TextChannel channel) {
        user.openPrivateChannel().queue(dm -> {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(EmojiUtil.wrap("✅") + " Vorschlag eingereicht!");
            embed.setDescription(
                    "Dein Vorschlag wurde erfolgreich erstellt!\n\n" +
                    "**Vorschlag-ID:** `" + suggestion.getSuggestionId() + "`\n" +
                    "**Channel:** " + channel.getAsMention() + "\n\n" +
                    EmojiUtil.wrap("📊") + " Die Community kann jetzt darüber abstimmen!\n" +
                    EmojiUtil.wrap("💬") + " Nutze den Thread für Diskussionen.\n\n" +
                    EmojiUtil.wrap("🔔") + " *Du wirst benachrichtigt, wenn sich der Status ändert.*"
            );
            embed.setColor(new Color(0x57F287));
            embed.setTimestamp(Instant.now());

            dm.sendMessageEmbeds(embed.build()).queue(
                    success -> logger.info("Sent confirmation DM to user {}", user.getId()),
                    error -> logger.warn("Could not send confirmation DM to user {}", user.getId())
            );
        });
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;
        if (event.getGuild() == null) return;

        String emoji = event.getReaction().getEmoji().asUnicode().getAsCodepoints();

        if (!emoji.equals(UPVOTE_EMOJI) && !emoji.equals(DOWNVOTE_EMOJI)) return;

        // Check if it's a suggestion message
        Optional<Suggestion> optSuggestion = SuggestionService.getSuggestionByMessage(
                event.getGuild().getId(),
                event.getMessageId()
        );

        if (optSuggestion.isEmpty()) return;

        Suggestion suggestion = optSuggestion.get();

        // Count current reactions
        event.retrieveMessage().queue(message -> {
            updateVoteCounts(message, suggestion);
        });
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;
        if (event.getGuild() == null) return;

        String emoji = event.getReaction().getEmoji().asUnicode().getAsCodepoints();

        if (!emoji.equals(UPVOTE_EMOJI) && !emoji.equals(DOWNVOTE_EMOJI)) return;

        // Check if it's a suggestion message
        Optional<Suggestion> optSuggestion = SuggestionService.getSuggestionByMessage(
                event.getGuild().getId(),
                event.getMessageId()
        );

        if (optSuggestion.isEmpty()) return;

        Suggestion suggestion = optSuggestion.get();

        // Count current reactions
        event.retrieveMessage().queue(message -> {
            updateVoteCounts(message, suggestion);
        });
    }

    private void updateVoteCounts(Message message, Suggestion suggestion) {
        int upvotes = 0;
        int downvotes = 0;

        for (MessageReaction reaction : message.getReactions()) {
            String emoji = reaction.getEmoji().asUnicode().getAsCodepoints();
            int count = reaction.getCount() - 1; // Subtract bot's reaction

            if (emoji.equals(UPVOTE_EMOJI)) {
                upvotes = Math.max(0, count);
            } else if (emoji.equals(DOWNVOTE_EMOJI)) {
                downvotes = Math.max(0, count);
            }
        }

        // Update suggestion
        suggestion.setUpvotes(upvotes);
        suggestion.setDownvotes(downvotes);
        SuggestionService.updateSuggestion(suggestion);

        // Update embed
        User author = message.getJDA().getUserById(suggestion.getUserId());
        if (author != null) {
            MessageEmbed newEmbed = createSuggestionEmbed(suggestion, author).build();
            message.editMessageEmbeds(newEmbed).queue(
                    success -> logger.debug("Updated vote counts for suggestion {}", suggestion.getSuggestionId()),
                    error -> logger.error("Failed to update embed for suggestion {}", suggestion.getSuggestionId(), error)
            );
        }
    }

    public static void updateSuggestionMessage(TextChannel channel, Suggestion suggestion) {
        if (suggestion.getMessageId() == null) return;

        channel.retrieveMessageById(suggestion.getMessageId()).queue(message -> {
            User author = channel.getJDA().getUserById(suggestion.getUserId());
            if (author == null) return;

            SuggestionListener listener = new SuggestionListener();
            MessageEmbed newEmbed = listener.createSuggestionEmbed(suggestion, author).build();
            message.editMessageEmbeds(newEmbed).queue();
        }, error -> logger.error("Failed to update suggestion message", error));
    }
}

