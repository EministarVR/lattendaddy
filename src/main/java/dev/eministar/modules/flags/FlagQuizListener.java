package dev.eministar.modules.flags;

import dev.eministar.config.Config;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class FlagQuizListener extends ListenerAdapter {

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!Config.getFlagQuizEnabled()) return; // Feature-Flag
        if (event.getAuthor().isBot()) return;
        var channel = event.getChannel();
        var guild = event.getGuild();
        if (guild == null) return;

        // Nur im konfigurierten Kanal
        String quizChannelId = FlagQuizService.getQuizChannelId(guild.getId());
        if (quizChannelId == null || !quizChannelId.equals(channel.getId())) return;

        String content = event.getMessage().getContentRaw();
        String prefix = Config.getPrefix();
        if (content.startsWith(prefix)) return; // Kommandos nicht stören

        // Antwortversuch an laufende Runde weiterreichen
        FlagQuizService.handleMessageAnswer(guild.getId(), channel.getId(), channel, event.getMember(), content);

        // User-Nachricht aus dem Quiz-Channel entfernen (aufgeräumt)
        event.getMessage().delete().queue(null, err -> {});

        // Dashboard Live-Refresh bei jeder Nachricht im Quiz-Channel
        if (channel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel tc) {
            FlagQuizService.tryEnsureDashboardMessage(guild.getId(), tc);
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!Config.getFlagQuizEnabled()) return;
        var guild = event.getGuild();
        if (guild == null) return;
        var chUnion = event.getChannel();
        String channelId = chUnion.getId();
        String quizChannelId = FlagQuizService.getQuizChannelId(guild.getId());
        if (quizChannelId == null || !quizChannelId.equals(channelId)) return;

        var msgChannel = event.getMessageChannel(); // zuverlässiger MessageChannel
        FlagQuizService.handleButton(guild.getId(), channelId, msgChannel, event.getMember(), event.getComponentId());
        event.deferEdit().queue(); // Button-Klick quittieren

        // Nach Button-Aktion Dashboard aktualisieren
        if (msgChannel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel tc) {
            FlagQuizService.tryEnsureDashboardMessage(guild.getId(), tc);
        }
    }
}
