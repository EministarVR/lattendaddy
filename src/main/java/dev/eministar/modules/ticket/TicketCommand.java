package dev.eministar.modules.ticket;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.Color;

public class TicketCommand implements Command {

    @Override
    public String name() {
        return "ticket";
    }

    @Override
    public String description() {
        return "Ticket-System verwalten";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getChannel().sendMessage(EmojiUtil.wrap("ℹ️") + " Bitte nutze `/ticket` für das Ticket-System!").queue();
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieser Command funktioniert nur auf Servern!").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler: Kein Subcommand gefunden!").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "setup" -> handleSetup(event);
            case "panel" -> handlePanel(event);
            default -> event.reply(EmojiUtil.wrap("❌") + " Unbekannter Befehl!").setEphemeral(true).queue();
        }
    }

    private void handleSetup(SlashCommandInteraction event) {
        // Check permissions
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst Administrator-Rechte!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> {
            // Setup will be done via Config
            hook.editOriginal(EmojiUtil.wrap("✅") + " Ticket-System wurde eingerichtet!\n" +
                    "Nutze `/ticket panel` um das Ticket-Panel zu senden.").queue();
        });
    }

    private void handlePanel(SlashCommandInteraction event) {
        // Check permissions
        if (event.getMember() == null || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply(EmojiUtil.wrap("❌") + " Du benötigst Administrator-Rechte!").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(EmojiUtil.wrap("🎫") + " Support Ticket System");
            embed.setDescription(
                    EmojiUtil.wrap("👋") + " **Willkommen beim Support!**\n\n" +
                    "Benötigst du Hilfe oder möchtest dich bewerben?\n" +
                    "Wähle einfach eine Kategorie aus dem Menü unten!\n\n" +
                    EmojiUtil.wrap("🎫") + " **Support** - Allgemeine Hilfe & Fragen\n" +
                    EmojiUtil.wrap("📝") + " **Bewerbung** - Bewirb dich im Team\n" +
                    EmojiUtil.wrap("⚠️") + " **Report** - Melde Regelverstöße\n" +
                    EmojiUtil.wrap("🎉") + " **Event** - Event-Ideen & Fragen\n\n" +
                    EmojiUtil.wrap("✨") + " *Unser Team hilft dir gerne weiter!*"
            );
            embed.setColor(new Color(0x5865F2));
            embed.setFooter("Lattendaddy Ticket System", null);
            if (event.getGuild() != null && event.getGuild().getIconUrl() != null) {
                embed.setThumbnail(event.getGuild().getIconUrl());
            }

            StringSelectMenu menu = StringSelectMenu.create("ticket:create")
                    .setPlaceholder(EmojiUtil.wrap("🎫") + " Wähle eine Kategorie...")
                    .addOption(TicketCategory.SUPPORT.getDisplayName(), "SUPPORT",
                            TicketCategory.SUPPORT.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🎫"))
                    .addOption(TicketCategory.BEWERBUNG.getDisplayName(), "BEWERBUNG",
                            TicketCategory.BEWERBUNG.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📝"))
                    .addOption(TicketCategory.REPORT.getDisplayName(), "REPORT",
                            TicketCategory.REPORT.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("⚠️"))
                    .addOption(TicketCategory.EVENT.getDisplayName(), "EVENT",
                            TicketCategory.EVENT.getDescription(),
                            net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🎉"))
                    .build();

            event.getChannel().sendMessageEmbeds(embed.build())
                    .setActionRow(menu)
                    .queue(
                        success -> hook.editOriginal(EmojiUtil.wrap("✅") + " Ticket-Panel wurde gesendet!").queue(),
                        error -> hook.editOriginal(EmojiUtil.wrap("❌") + " Fehler beim Senden des Panels!").queue()
                    );
        });
    }

    @Override
    public net.dv8tion.jda.api.interactions.commands.build.CommandData getSlashCommandData() {
        return Commands.slash("ticket", description())
                .addSubcommands(
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("setup", "Richte das Ticket-System ein"),
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("panel", "Sende das Ticket-Panel")
                );
    }
}


