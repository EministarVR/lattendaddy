package dev.eministar.modules.ticket;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class TicketListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TicketListener.class);

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("ticket:create")) return;
        if (event.getGuild() == null) return;

        String categoryValue = event.getValues().get(0);
        TicketCategory category;

        try {
            category = TicketCategory.valueOf(categoryValue);
        } catch (IllegalArgumentException e) {
            event.reply(EmojiUtil.wrap("❌") + " Ungültige Kategorie!").setEphemeral(true).queue();
            return;
        }

        // Check if user already has an open ticket
        List<Ticket> userTickets = TicketService.getUserTickets(event.getGuild().getId(), event.getUser().getId());
        long openTickets = userTickets.stream()
                .filter(t -> t.getStatus() != Ticket.TicketStatus.CLOSED)
                .count();

        if (openTickets >= 3) {
            event.reply(EmojiUtil.wrap("❌") + " Du hast bereits 3 offene Tickets! Bitte schließe erst ein Ticket, bevor du ein neues erstellst.")
                    .setEphemeral(true).queue();
            return;
        }

        // Show modal to ask for reason
        showReasonModal(event, category);
    }

    private void showReasonModal(StringSelectInteractionEvent event, TicketCategory category) {
        String modalId = "ticket:reason:" + category.name();

        TextInput reasonInput = TextInput.create("reason", getReasonLabel(category), TextInputStyle.PARAGRAPH)
                .setPlaceholder(getReasonPlaceholder(category))
                .setMinLength(10)
                .setMaxLength(500)
                .setRequired(true)
                .build();

        Modal modal = Modal.create(modalId, category.getEmoji() + " " + category.getDisplayName())
                .addActionRow(reasonInput)
                .build();

        event.replyModal(modal).queue();
    }

    private String getReasonLabel(TicketCategory category) {
        return switch (category) {
            case SUPPORT -> "Was ist dein Anliegen?";
            case BEWERBUNG -> "Für welche Position bewirbst du dich?";
            case REPORT -> "Was möchtest du melden?";
            case EVENT -> "Welche Event-Idee hast du?";
        };
    }

    private String getReasonPlaceholder(TicketCategory category) {
        return switch (category) {
            case SUPPORT -> "Beschreibe dein Problem oder deine Frage...";
            case BEWERBUNG -> "z.B. Moderator, Developer, Designer...";
            case REPORT -> "Beschreibe den Regelverstoß...";
            case EVENT -> "Beschreibe deine Event-Idee...";
        };
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        // Notiz-Modal zuerst behandeln
        if (event.getModalId().startsWith("ticket:addnote:")) {
            if (event.getGuild() == null) return;
            String ticketId = event.getModalId().split(":")[2];
            Ticket ticket = TicketService.getTicket(event.getGuild().getId(), ticketId).orElse(null);
            if (ticket == null) {
                event.reply(EmojiUtil.wrap("❌") + " Ticket nicht gefunden!").setEphemeral(true).queue();
                return;
            }
            var noteValue = event.getValue("note");
            if (noteValue == null) {
                event.reply(EmojiUtil.wrap("❌") + " Keine Notiz enthalten!").setEphemeral(true).queue();
                return;
            }
            ticket.addNote(new Ticket.TicketNote(event.getUser().getId(), noteValue.getAsString()));
            TicketService.updateTicket(ticket);
            event.reply(EmojiUtil.wrap("✅") + " Notiz hinzugefügt.").setEphemeral(true).queue();
            return;
        }

        if (!event.getModalId().startsWith("ticket:reason:")) return;
        if (event.getGuild() == null) return;

        String categoryName = event.getModalId().replace("ticket:reason:", "");
        TicketCategory category;

        try {
            category = TicketCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler beim Erstellen des Tickets!").setEphemeral(true).queue();
            return;
        }

        var reasonValue = event.getValue("reason");
        if (reasonValue == null) {
            event.reply(EmojiUtil.wrap("❌") + " Bitte gib einen Grund an!").setEphemeral(true).queue();
            return;
        }

        String reason = reasonValue.getAsString();
        Guild guild = event.getGuild();
        User user = event.getUser();

        event.deferReply(true).queue(hook ->
            createTicketChannel(guild, user, category, reason, hook)
        );
    }

    private void createTicketChannel(Guild guild, User user, TicketCategory category, String reason,
                                     net.dv8tion.jda.api.interactions.InteractionHook hook) {
        // Create ticket in database
        Ticket ticket = TicketService.createTicket(guild.getId(), user.getId(), category);
        ticket.setReason(reason);
        TicketService.updateTicket(ticket);

        // Check if category exists, if not create it
        List<Category> categories = guild.getCategoriesByName("📥 Tickets", true);
        if (!categories.isEmpty()) {
            // Category exists
            createChannel(categories.get(0), guild, user, ticket, reason, hook);
        } else {
            // Create category first
            guild.createCategory("📥 Tickets").queue(
                newCategory -> createChannel(newCategory, guild, user, ticket, reason, hook),
                error -> {
                    logger.error("Error creating ticket category", error);
                    hook.editOriginal(EmojiUtil.wrap("❌") + " Fehler: Konnte Ticket-Kategorie nicht erstellen!").queue();
                }
            );
        }
    }

    private void createChannel(Category ticketCategory, Guild guild, User user, Ticket ticket, String reason,
                               net.dv8tion.jda.api.interactions.InteractionHook hook) {
        String channelName = ticket.getCategory().name().toLowerCase() + "-" + ticket.getTicketId().substring(7);

        ticketCategory.createTextChannel(channelName)
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(user.getIdLong(),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                .queue(channel -> {
                    ticket.setChannelId(channel.getId());
                    TicketService.updateTicket(ticket);

                    sendWelcomeMessage(channel, user, ticket, reason);
                    sendDMNotification(user, ticket, channel);
                    logTicketCreation(guild, user, ticket);

                    hook.editOriginal(EmojiUtil.wrap("✅") + " Ticket erstellt! " + channel.getAsMention()).queue();
                }, error -> {
                    logger.error("Error creating ticket channel", error);
                    hook.editOriginal(EmojiUtil.wrap("❌") + " Fehler beim Erstellen des Ticket-Channels!").queue();
                });
    }

    private void sendWelcomeMessage(TextChannel channel, User user, Ticket ticket, String reason) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(ticket.getCategory().getEmoji() + " " + ticket.getCategory().getDisplayName() + " - " + ticket.getTicketId());
        embed.setDescription(
                EmojiUtil.wrap("👋") + " **Willkommen, " + user.getAsMention() + "!**\n\n" +
                getCategoryWelcomeMessage(ticket.getCategory()) + "\n\n" +
                EmojiUtil.wrap("📝") + " **Dein Anliegen:**\n```\n" + reason + "\n```\n\n" +
                EmojiUtil.wrap("⏰") + " **Bitte habe etwas Geduld!**\n" +
                "Unser Team wird sich so schnell wie möglich um dein Anliegen kümmern.\n\n" +
                EmojiUtil.wrap("ℹ️") + " *Nutze die Buttons unten um das Ticket zu verwalten.*"
        );
        embed.setColor(getCategoryColor(ticket.getCategory()));
        embed.setThumbnail(user.getAvatarUrl());
        embed.setFooter("Ticket erstellt", null);
        embed.setTimestamp(Instant.now());

        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.success("ticket:claim:" + ticket.getTicketId(), EmojiUtil.wrap("✋") + " Claim"));
        buttons.add(Button.danger("ticket:close:" + ticket.getTicketId(), EmojiUtil.wrap("🔒") + " Close"));
        buttons.add(Button.primary("ticket:note:" + ticket.getTicketId(), EmojiUtil.wrap("📝") + " Notiz"));
        buttons.add(Button.secondary("ticket:prio:" + ticket.getTicketId(), EmojiUtil.wrap("⚙️") + " Prio"));

        if (ticket.getCategory() == TicketCategory.BEWERBUNG) {
            buttons.add(Button.primary("ticket:accept:" + ticket.getTicketId(), EmojiUtil.wrap("✅") + " Akzeptieren"));
            buttons.add(Button.secondary("ticket:reject:" + ticket.getTicketId(), EmojiUtil.wrap("❌") + " Ablehnen"));
        }

        channel.sendMessage(user.getAsMention()).queue(); // Ping user
        channel.sendMessageEmbeds(embed.build())
                .setActionRow(buttons)
                .queue();
    }

    private String getCategoryWelcomeMessage(TicketCategory category) {
        return switch (category) {
            case SUPPORT -> EmojiUtil.wrap("🎫") + " **Support-Team wird dir helfen!**\nBeschreibe dein Problem so detailliert wie möglich.";
            case BEWERBUNG -> EmojiUtil.wrap("📝") + " **Danke für dein Interesse!**\nBitte erzähle uns etwas über dich und warum du dich bewirbst.";
            case REPORT -> EmojiUtil.wrap("⚠️") + " **Report wird geprüft!**\nBitte füge Screenshots oder weitere Beweise hinzu.";
            case EVENT -> EmojiUtil.wrap("🎉") + " **Event-Team ist interessiert!**\nTeil uns mehr Details zu deiner Idee mit.";
        };
    }

    private Color getCategoryColor(TicketCategory category) {
        return switch (category) {
            case SUPPORT -> new Color(0x5865F2);
            case BEWERBUNG -> new Color(0x57F287);
            case REPORT -> new Color(0xED4245);
            case EVENT -> new Color(0xFEE75C);
        };
    }

    private void sendDMNotification(User user, Ticket ticket, TextChannel channel) {
        user.openPrivateChannel().queue(dm -> {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(EmojiUtil.wrap("🎫") + " Ticket erstellt!");
            embed.setDescription(
                    "Dein Ticket wurde erfolgreich erstellt!\n\n" +
                    "**Ticket-ID:** `" + ticket.getTicketId() + "`\n" +
                    "**Kategorie:** " + ticket.getCategory().getFormattedName() + "\n" +
                    "**Channel:** " + channel.getAsMention() + "\n\n" +
                    EmojiUtil.wrap("✨") + " *Unser Team wird sich bald bei dir melden!*"
            );
            embed.setColor(new Color(0x5865F2));
            embed.setTimestamp(Instant.now());

            dm.sendMessageEmbeds(embed.build()).queue(
                    success -> logger.info("DM sent to user {} for ticket {}", user.getId(), ticket.getTicketId()),
                    error -> logger.warn("Could not send DM to user {} for ticket {}", user.getId(), ticket.getTicketId())
            );
        }, error -> logger.warn("Could not open DM channel for user {}", user.getId()));
    }

    private void logTicketCreation(Guild guild, User user, Ticket ticket) {
        String logChannelId = Config.getTicketLogChannelId();
        if (logChannelId.isEmpty()) return;

        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("📝") + " Ticket erstellt");
        embed.addField("Ticket-ID", ticket.getTicketId(), true);
        embed.addField("Kategorie", ticket.getCategory().getFormattedName(), true);
        embed.addField("User", user.getAsMention() + "\n`" + user.getId() + "`", true);
        embed.addField("Grund", ticket.getReason(), false);
        embed.setColor(new Color(0x57F287));
        embed.setTimestamp(Instant.now());
        embed.setThumbnail(user.getAvatarUrl());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("ticket:claim:")) {
            handleClaim(event);
        } else if (componentId.startsWith("ticket:close:")) {
            handleClose(event);
        } else if (componentId.startsWith("ticket:accept:")) {
            handleAccept(event);
        } else if (componentId.startsWith("ticket:reject:")) {
            handleReject(event);
        } else if (componentId.startsWith("ticket:note:")) {
            handleAddNote(event);
        } else if (componentId.startsWith("ticket:prio:")) {
            handleTogglePriority(event);
        }
    }

    private boolean hasAnyClaimRole(Member member) {
        List<String> allowed = Config.getTicketClaimRoleIds();
        if (allowed.isEmpty()) return true; // Fallback: wenn nicht konfiguriert, alle erlauben
        for (Role role : member.getRoles()) {
            if (allowed.contains(role.getId())) return true;
        }
        return false;
    }

    private void handleAddNote(ButtonInteractionEvent event) {
        if (event.getGuild() == null) return;
        String ticketId = event.getComponentId().split(":")[2];
        Ticket ticket = TicketService.getTicket(event.getGuild().getId(), ticketId).orElse(null);
        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Ticket nicht gefunden!").setEphemeral(true).queue();
            return;
        }
        TextInput input = TextInput.create("note", "Notiz", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Interne Notiz hinzufügen...")
                .setRequired(true)
                .setMaxLength(300)
                .build();
        Modal modal = Modal.create("ticket:addnote:" + ticketId, "Notiz für " + ticket.getTicketId())
                .addActionRow(input)
                .build();
        event.replyModal(modal).queue();
    }

    private void handleTogglePriority(ButtonInteractionEvent event) {
        if (event.getGuild() == null) return;
        String ticketId = event.getComponentId().split(":")[2];
        Ticket ticket = TicketService.getTicket(event.getGuild().getId(), ticketId).orElse(null);
        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Ticket nicht gefunden!").setEphemeral(true).queue();
            return;
        }
        Ticket.TicketPriority next = switch (ticket.getPriority()) {
            case LOW -> Ticket.TicketPriority.NORMAL;
            case NORMAL -> Ticket.TicketPriority.HIGH;
            case HIGH -> Ticket.TicketPriority.LOW;
        };
        ticket.setPriority(next);
        TicketService.updateTicket(ticket);
        event.reply(EmojiUtil.wrap("⚙️") + " Priorität ist jetzt **" + next.name() + "**").setEphemeral(true).queue();
    }

    private void handleClaim(ButtonInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) return;

        // Rollenprüfung
        if (!hasAnyClaimRole(event.getMember())) {
            event.reply(EmojiUtil.wrap("⛔") + " Du darfst keine Tickets claimen.").setEphemeral(true).queue();
            return;
        }

        String ticketId = event.getComponentId().replace("ticket:claim:", "");
        Ticket ticket = TicketService.getTicket(event.getGuild().getId(), ticketId).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Ticket nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        if (ticket.getClaimedBy() != null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieses Ticket wurde bereits geclaimt!").setEphemeral(true).queue();
            return;
        }

        ticket.setClaimedBy(event.getUser().getId());
        ticket.setStatus(Ticket.TicketStatus.CLAIMED);
        TicketService.updateTicket(ticket);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(
                EmojiUtil.wrap("✋") + " **Ticket geclaimt!**\n\n" +
                event.getUser().getAsMention() + " kümmert sich jetzt um dieses Ticket."
        );
        embed.setColor(new Color(0xFEE75C));
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();

        // Update channel permissions
        event.getChannel().asTextChannel().getManager()
                .putMemberPermissionOverride(event.getMember().getIdLong(),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
                .queue();

        logTicketClaim(event.getGuild(), event.getUser(), ticket);
    }

    private void handleClose(ButtonInteractionEvent event) {
        if (event.getGuild() == null) return;

        Ticket ticket = TicketService.getTicketByChannel(event.getGuild().getId(), event.getChannel().getId()).orElse(null);

        if (ticket == null) {
            event.reply(EmojiUtil.wrap("❌") + " Ticket nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue(hook -> {
            ticket.setStatus(Ticket.TicketStatus.CLOSED);
            ticket.setClosedAt(System.currentTimeMillis());
            TicketService.updateTicket(ticket);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(EmojiUtil.wrap("🔒") + " Ticket wird geschlossen");
            embed.setDescription(
                    "Dieses Ticket wird in 5 Sekunden geschlossen.\n\n" +
                    "**Geschlossen von:** " + event.getUser().getAsMention() + "\n" +
                    EmojiUtil.wrap("💾") + " *Das Ticket wird gespeichert.*"
            );
            embed.setColor(new Color(0xED4245));
            embed.setTimestamp(Instant.now());

            hook.editOriginalEmbeds(embed.build()).queue();

            // Close channel after 5 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    logTicketClose(event.getGuild(), event.getUser(), ticket);

                    // Transcript als einfache Zusammenfassung in den Log-Kanal
                    String logChannelId = Config.getTicketLogChannelId();
                    if (logChannelId != null && !logChannelId.isEmpty()) {
                        TextChannel logChannel = event.getGuild().getTextChannelById(logChannelId);
                        if (logChannel != null) {
                            StringBuilder transcript = new StringBuilder();
                            transcript.append("Ticket-ID: ").append(ticket.getTicketId()).append("\n");
                            transcript.append("User: ").append(ticket.getUserId()).append("\n");
                            transcript.append("Kategorie: ").append(ticket.getCategory().name()).append("\n");
                            transcript.append("Grund: ").append(ticket.getReason()).append("\n");
                            transcript.append("Priorität: ").append(ticket.getPriority()).append("\n");
                            if (!ticket.getNotes().isEmpty()) {
                                transcript.append("Notizen:\n");
                                for (Ticket.TicketNote note : ticket.getNotes()) {
                                    transcript.append(" - [").append(note.getTimestamp()).append("] ")
                                            .append(note.getAuthorId()).append(": ")
                                            .append(note.getContent()).append("\n");
                                }
                            }
                            logChannel.sendMessage(EmojiUtil.wrap("📄") + " Transcript von " + ticket.getTicketId() + "\n```\n" + transcript + "```")
                                    .queue();
                        }
                    }

                    event.getChannel().asTextChannel().delete().queue();
                } catch (Exception e) {
                    logger.error("Error closing ticket channel", e);
                }
            }).start();
        });
    }

    private void handleAccept(ButtonInteractionEvent event) {
        handleApplication(event, true);
    }

    private void handleReject(ButtonInteractionEvent event) {
        handleApplication(event, false);
    }

    private void handleApplication(ButtonInteractionEvent event, boolean accepted) {
        if (event.getGuild() == null) return;

        String ticketId = event.getComponentId().split(":")[2];
        Ticket ticket = TicketService.getTicket(event.getGuild().getId(), ticketId).orElse(null);

        if (ticket == null || ticket.getCategory() != TicketCategory.BEWERBUNG) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler!").setEphemeral(true).queue();
            return;
        }

        User applicant = event.getJDA().getUserById(ticket.getUserId());
        if (applicant == null) {
            event.reply(EmojiUtil.wrap("❌") + " Bewerber nicht gefunden!").setEphemeral(true).queue();
            return;
        }

        String status = accepted ? "akzeptiert" : "abgelehnt";
        String emoji = accepted ? "✅" : "❌";
        Color color = accepted ? new Color(0x57F287) : new Color(0xED4245);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setDescription(
                EmojiUtil.wrap(emoji) + " **Bewerbung " + status + "!**\n\n" +
                "**Bewerber:** " + applicant.getAsMention() + "\n" +
                "**Entschieden von:** " + event.getUser().getAsMention()
        );
        embed.setColor(color);
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();

        // Send DM to applicant
        applicant.openPrivateChannel().queue(dm -> {
            EmbedBuilder dmEmbed = new EmbedBuilder();
            dmEmbed.setTitle(EmojiUtil.wrap(emoji) + " Bewerbung " + status);
            dmEmbed.setDescription(
                    accepted
                        ? "Herzlichen Glückwunsch! Deine Bewerbung wurde akzeptiert!\n\nEin Teammitglied wird sich bei dir melden."
                        : "Leider wurde deine Bewerbung abgelehnt.\n\nDu kannst dich gerne zu einem späteren Zeitpunkt erneut bewerben."
            );
            dmEmbed.setColor(color);
            dmEmbed.setTimestamp(Instant.now());
            dm.sendMessageEmbeds(dmEmbed.build()).queue();
        });

        logApplicationDecision(event.getGuild(), event.getUser(), applicant, ticket, accepted);
    }

    private void logTicketClaim(Guild guild, User claimer, Ticket ticket) {
        String logChannelId = Config.getTicketLogChannelId();
        if (logChannelId.isEmpty()) return;

        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("✋") + " Ticket geclaimt");
        embed.addField("Ticket-ID", ticket.getTicketId(), true);
        embed.addField("Geclaimt von", claimer.getAsMention(), true);
        embed.setColor(new Color(0xFEE75C));
        embed.setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    private void logTicketClose(Guild guild, User closer, Ticket ticket) {
        String logChannelId = Config.getTicketLogChannelId();
        if (logChannelId.isEmpty()) return;

        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap("🔒") + " Ticket geschlossen");
        embed.addField("Ticket-ID", ticket.getTicketId(), true);
        embed.addField("Kategorie", ticket.getCategory().getFormattedName(), true);
        embed.addField("Geschlossen von", closer.getAsMention(), true);
        embed.addField("User", "<@" + ticket.getUserId() + ">", true);

        long duration = ticket.getClosedAt() - ticket.getCreatedAt();
        long minutes = duration / (1000 * 60);
        embed.addField("Dauer", minutes + " Minuten", true);

        embed.setColor(new Color(0xED4245));
        embed.setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }

    private void logApplicationDecision(Guild guild, User decider, User applicant, Ticket ticket, boolean accepted) {
        String logChannelId = Config.getTicketLogChannelId();
        if (logChannelId.isEmpty()) return;

        TextChannel logChannel = guild.getTextChannelById(logChannelId);
        if (logChannel == null) return;

        String status = accepted ? "akzeptiert" : "abgelehnt";
        String emoji = accepted ? "✅" : "❌";

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(EmojiUtil.wrap(emoji) + " Bewerbung " + status);
        embed.addField("Ticket-ID", ticket.getTicketId(), true);
        embed.addField("Bewerber", applicant.getAsMention(), true);
        embed.addField("Entschieden von", decider.getAsMention(), true);
        embed.addField("Grund", ticket.getReason(), false);
        embed.setColor(accepted ? new Color(0x57F287) : new Color(0xED4245));
        embed.setTimestamp(Instant.now());

        logChannel.sendMessageEmbeds(embed.build()).queue();
    }
}

