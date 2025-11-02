package dev.eministar.modules.moderation;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClearCommand implements Command {

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String description() {
        return "Löscht Nachrichten aus dem Channel";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Nachrichten verwalten** Berechtigung!").queue();
            return;
        }

        if (args.length < 1) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib die Anzahl der zu löschenden Nachrichten an!\n" +
                    "**Verwendung:** `.clear <anzahl>`").queue();
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib eine gültige Zahl an!").queue();
            return;
        }

        if (amount < 1 || amount > 100) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du kannst nur zwischen **1** und **100** Nachrichten löschen!").queue();
            return;
        }

        // Lösche Command-Nachricht
        event.getMessage().delete().queue();

        // Lösche Nachrichten
        TextChannel channel = event.getChannel().asTextChannel();
        channel.getIterableHistory()
                .takeAsync(amount)
                .thenAccept(messages -> {
                    if (messages.isEmpty()) {
                        channel.sendMessage(EmojiUtil.wrap("ℹ️") + " Keine Nachrichten zum Löschen gefunden!")
                                .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }

                    // Discord erlaubt nur Bulk-Delete für Nachrichten < 14 Tage alt
                    List<Message> bulkDeletableMessages = messages.stream()
                            .filter(msg -> !msg.getTimeCreated().isBefore(
                                    msg.getTimeCreated().minusWeeks(2)))
                            .toList();

                    if (bulkDeletableMessages.isEmpty()) {
                        channel.sendMessage(EmojiUtil.wrap("❌") + " Alle Nachrichten sind älter als 14 Tage und können nicht gelöscht werden!")
                                .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }

                    if (bulkDeletableMessages.size() == 1) {
                        bulkDeletableMessages.get(0).delete().queue(
                                success -> channel.sendMessage(EmojiUtil.wrap("✅") + " **1** Nachricht gelöscht!")
                                        .queue(msg -> msg.delete().queueAfter(3, TimeUnit.SECONDS)),
                                error -> channel.sendMessage(EmojiUtil.wrap("❌") + " Fehler beim Löschen!")
                                        .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS))
                        );
                    } else {
                        channel.deleteMessages(bulkDeletableMessages).queue(
                                success -> channel.sendMessage(EmojiUtil.wrap("✅") + " **" + bulkDeletableMessages.size() + "** Nachrichten gelöscht!")
                                        .queue(msg -> msg.delete().queueAfter(3, TimeUnit.SECONDS)),
                                error -> channel.sendMessage(EmojiUtil.wrap("❌") + " Fehler beim Löschen!")
                                        .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS))
                        );
                    }
                });
    }

    @Override
    public CommandData getSlashCommandData() {
        return null; // Nur als Text-Command
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        // Nicht verwendet
    }
}