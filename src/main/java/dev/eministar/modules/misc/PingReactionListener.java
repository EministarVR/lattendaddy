package dev.eministar.modules.misc;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PingReactionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(PingReactionListener.class);
    private static final String CUSTOM_EMOJI_ID = "1434377271027699893";
    private static final String CUSTOM_EMOJI_NAME = "md";

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        // Verhindere Reaktion auf @everyone / @here oder globale Mentions nur wenn direkt der Bot erwähnt ist
        if (event.getMessage().getMentions().isMentioned(event.getJDA().getSelfUser())
                && !event.getMessage().getMentions().mentionsEveryone()) {
            try {
                event.getMessage().addReaction(Emoji.fromCustom(CUSTOM_EMOJI_NAME, Long.parseLong(CUSTOM_EMOJI_ID), false)).queue(
                        success -> logger.debug("Reacted to direct bot ping with custom emoji"),
                        error -> logger.error("Failed to react with custom emoji", error)
                );
            } catch (Exception e) {
                logger.error("Error adding reaction", e);
            }
        }
    }
}
