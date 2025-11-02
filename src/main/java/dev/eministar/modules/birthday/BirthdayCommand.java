package dev.eministar.modules.birthday;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

public class BirthdayCommand implements Command {
    @Override
    public String name() {
        return "birthday";
    }

    @Override
    public String description() {
        return "Verwalte Geburtstage: set/remove/list";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (args.length == 0) {
            event.getChannel().sendMessage("Benutze: " + EmojiUtil.wrap("🎂") + "birthday set <tag> <monat> [jahr] oder birthday list").queue();
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("set")) {
            if (args.length < 3) {
                event.getChannel().sendMessage("Usage: birthday set <day> <month> [year]").queue();
                return;
            }
            try {
                int day = Integer.parseInt(args[1]);
                int month = Integer.parseInt(args[2]);
                Integer year = null;
                if (args.length >= 4) year = Integer.parseInt(args[3]);
                String guildId = event.getGuild().getId();
                String userId = event.getAuthor().getId();
                BirthdayService.setBirthday(guildId, userId, day, month, year);
                event.getChannel().sendMessage(EmojiUtil.wrap("🎉") + "Geburtstag gesetzt: " + new BirthdayService.BirthdayEntry(day, month, year, "", "").pretty()).queue();

                // update list embed if present
                BirthdayListener.updateListEmbed(event.getGuild().getId(), event.getJDA());
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("Ungültige Zahlen. Tag und Monat müssen Zahlen sein.").queue();
            }
        } else if (sub.equals("remove")) {
            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            BirthdayService.removeBirthday(guildId, userId);
            event.getChannel().sendMessage(EmojiUtil.wrap("🗑️") + "Dein Geburtstag wurde entfernt.").queue();
            BirthdayListener.updateListEmbed(event.getGuild().getId(), event.getJDA());
        } else if (sub.equals("list")) {
            BirthdayListener.sendListEmbed(event.getGuild().getId(), event.getChannel().asTextChannel());
        } else {
            event.getChannel().sendMessage("Unbekannter Unterbefehl: " + sub).queue();
        }
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) {
            event.reply(EmojiUtil.wrap("❌") + " Dieser Command funktioniert nur in Servern!").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler: Kein Subcommand gefunden!").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "set" -> {
                var dayOpt = event.getOption("day");
                var monthOpt = event.getOption("month");

                if (dayOpt == null || monthOpt == null) {
                    event.reply(EmojiUtil.wrap("❌") + " Bitte gib Tag und Monat an!").setEphemeral(true).queue();
                    return;
                }

                int day = (int) dayOpt.getAsLong();
                int month = (int) monthOpt.getAsLong();
                Integer year = null;

                var yearOpt = event.getOption("year");
                if (yearOpt != null) {
                    year = (int) yearOpt.getAsLong();
                }

                // Validate date
                if (day < 1 || day > 31 || month < 1 || month > 12) {
                    event.reply(EmojiUtil.wrap("❌") + " Ungültiges Datum! Tag muss 1-31 und Monat muss 1-12 sein.").setEphemeral(true).queue();
                    return;
                }

                String guildId = event.getGuild().getId();
                String userId = event.getUser().getId();
                BirthdayService.setBirthday(guildId, userId, day, month, year);

                event.reply(EmojiUtil.wrap("🎉") + " Geburtstag erfolgreich gesetzt: " +
                        new BirthdayService.BirthdayEntry(day, month, year, "", "").pretty())
                        .setEphemeral(true)
                        .queue();

                // Update list asynchronously, completely independent of the interaction
                final String finalGuildId = guildId;
                final JDA finalJDA = event.getJDA();
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // Small delay to ensure interaction is fully processed
                        BirthdayListener.updateListEmbed(finalGuildId, finalJDA);
                    } catch (Exception ignored) {
                        // Silent fail, list will be updated on next bot restart
                    }
                }).start();
            }
            case "remove" -> {
                String guildId = event.getGuild().getId();
                String userId = event.getUser().getId();

                if (BirthdayService.getBirthday(guildId, userId).isEmpty()) {
                    event.reply(EmojiUtil.wrap("❌") + " Du hast keinen Geburtstag eingetragen!").setEphemeral(true).queue();
                    return;
                }

                BirthdayService.removeBirthday(guildId, userId);
                event.reply(EmojiUtil.wrap("🗑️") + " Dein Geburtstag wurde entfernt.")
                        .setEphemeral(true)
                        .queue();

                // Update list asynchronously, completely independent of the interaction
                final String finalGuildId = guildId;
                final JDA finalJDA = event.getJDA();
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // Small delay to ensure interaction is fully processed
                        BirthdayListener.updateListEmbed(finalGuildId, finalJDA);
                    } catch (Exception ignored) {
                        // Silent fail, list will be updated on next bot restart
                    }
                }).start();
            }
            case "list" -> {
                String guildId = event.getGuild().getId();
                JDA jda = event.getJDA();
                event.deferReply().queue(hook -> {
                    try {
                        BirthdayListener.sendListEmbedToHook(guildId, hook, jda);
                    } catch (Exception e) {
                        hook.editOriginal(EmojiUtil.wrap("❌") + " Fehler beim Laden der Geburtstagsliste!").queue();
                    }
                });
            }
            default -> event.reply(EmojiUtil.wrap("❌") + " Unbekannter Befehl!").setEphemeral(true).queue();
        }
    }

    @Override
    public net.dv8tion.jda.api.interactions.commands.build.CommandData getSlashCommandData() {
        return Commands.slash("birthday", description())
                .addSubcommands(
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("set", "Setze deinen Geburtstag")
                                .addOption(OptionType.INTEGER, "day", "Tag (1-31)", true)
                                .addOption(OptionType.INTEGER, "month", "Monat (1-12)", true)
                                .addOption(OptionType.INTEGER, "year", "Jahr (optional)", false),
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("remove", "Entferne deinen Geburtstag"),
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("list", "Zeige alle Geburtstage")
                );
    }
}

