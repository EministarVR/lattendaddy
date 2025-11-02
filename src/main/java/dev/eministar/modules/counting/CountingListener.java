package dev.eministar.modules.counting;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;

public class CountingListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CountingListener.class);

    private boolean enabled;
    private String countingChannelId;

    public CountingListener() {
        loadConfig();
    }

    private void loadConfig() {
        enabled = Config.getCountingEnabled();
        countingChannelId = Config.getCountingChannelId();
        logger.info("Counting Module - Enabled: {}, Channel: {}", enabled, countingChannelId);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!enabled || event.getAuthor().isBot()) return;
        if (!event.getChannel().getId().equals(countingChannelId)) return;

        String content = event.getMessage().getContentRaw().trim();
        String userId = event.getAuthor().getId();
        String channelId = event.getChannel().getId();

        CountingGame.GameState game = CountingGame.getOrCreateGame(channelId);

        // Prüfe ob der User zweimal hintereinander zählt
        if (userId.equals(game.getLastUserId())) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " **Du darfst nicht zweimal hintereinander zählen!**\n" +
                    EmojiUtil.wrap("🔄") + " Zurück auf **1**! Der Highscore war **" + (game.getCurrentNumber() - 1) + "**!").queue();

            game.incrementTotalFails();
            game.reset();
            CountingGame.updateGame(channelId, game);

            event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue();
            return;
        }

        // Prüfe ob es eine gültige Zahl/Rechnung ist
        if (!CountingGame.isValidNumber(content)) {
            // Ignoriere Nachrichten die keine Zahlen sind (z.B. normaler Chat)
            return;
        }

        // Berechne den Wert
        Integer value = CountingGame.evaluateExpression(content);
        if (value == null) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " **Ungültige Rechnung!**\n" +
                    EmojiUtil.wrap("🔄") + " Zurück auf **1**! Der Highscore war **" + (game.getCurrentNumber() - 1) + "**!").queue();

            game.incrementTotalFails();
            game.reset();
            CountingGame.updateGame(channelId, game);

            event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue();
            return;
        }

        // Prüfe ob die Zahl korrekt ist
        if (value != game.getCurrentNumber()) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " **Falsch gezählt!** Es wäre **" + game.getCurrentNumber() + "** gewesen, aber du hast **" + value + "** geschrieben!\n" +
                    EmojiUtil.wrap("🔄") + " Zurück auf **1**! Der Highscore war **" + (game.getCurrentNumber() - 1) + "**!").queue();

            game.incrementTotalFails();
            game.reset();
            CountingGame.updateGame(channelId, game);

            event.getMessage().addReaction(Emoji.fromUnicode("❌")).queue();
            return;
        }

        // Korrekt gezählt!
        game.incrementTotalCounts();
        game.setCurrentNumber(game.getCurrentNumber() + 1);
        game.setLastUserId(userId);
        CountingGame.updateGame(channelId, game);

        // Reagiere mit Haken
        event.getMessage().addReaction(Emoji.fromUnicode("✅")).queue();

        // Bei besonderen Meilensteinen eine Nachricht senden
        int currentCount = value;
        if (currentCount % 100 == 0) {
            sendMilestoneMessage(event, game, currentCount);
        } else if (currentCount > game.getHighscore() && game.getHighscore() > 0 && currentCount % 10 == 0) {
            // Neuer Rekord in 10er-Schritten
            event.getChannel().sendMessage(EmojiUtil.wrap("🎉") + " **Neuer Rekord!** Ihr seid jetzt bei **" + currentCount + "**! Weiter so!").queue();
        }

        logger.debug("Correct count in channel {}: {} by user {}", channelId, currentCount, userId);
    }

    private void sendMilestoneMessage(MessageReceivedEvent event, CountingGame.GameState game, int milestone) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🎉") + " Meilenstein erreicht!");
        embed.setDescription("**" + milestone + "** wurde erreicht!");
        embed.addField(EmojiUtil.wrap("🏆") + " Highscore", String.valueOf(game.getHighscore()), true);
        embed.addField(EmojiUtil.wrap("📊") + " Gesamt gezählt", String.valueOf(game.getTotalCounts()), true);
        embed.addField(EmojiUtil.wrap("💥") + " Gesamt Fails", String.valueOf(game.getTotalFails()), true);
        embed.setColor(new Color(0xFFD700));
        embed.setTimestamp(Instant.now());
        embed.setFooter("Weiter so! 💪", event.getJDA().getSelfUser().getAvatarUrl());

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
}

