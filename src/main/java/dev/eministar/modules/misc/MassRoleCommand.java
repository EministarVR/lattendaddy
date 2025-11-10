package dev.eministar.modules.misc;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MassRoleCommand implements Command {
    @Override
    public String name() { return "massrole"; }

    @Override
    public String description() { return "Massen-Rollenverwaltung (nur Server-Inhaber)"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getMessage().reply(EmojiUtil.wrap("ℹ️") + " Bitte nutze den Slash-Command `/massrole`!").queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addSubcommands(
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("add", "Füge einer Gruppe von Usern eine Rolle hinzu")
                                .addOptions(
                                        new OptionData(OptionType.ROLE, "rolle", "Rolle, die hinzugefügt werden soll", true),
                                        new OptionData(OptionType.STRING, "filter", "Filter: all|no-role|has:<roleId>", false),
                                        new OptionData(OptionType.BOOLEAN, "dryrun", "Nur anzeigen wie viele betroffen wären (keine Änderungen)", false)
                                )
                );
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) {
            event.reply(EmojiUtil.wrap("❌") + " Nur in Servern nutzbar!").setEphemeral(true).queue();
            return;
        }
        if (event.getMember() == null || !event.getMember().isOwner()) {
            event.reply(EmojiUtil.wrap("⛔") + " Nur der Server-Inhaber kann diesen Befehl nutzen!").setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply(EmojiUtil.wrap("❌") + " Kein Subcommand angegeben!").setEphemeral(true).queue();
            return;
        }

        if ("add".equals(sub)) {
            handleAdd(event);
            return;
        }
        event.reply(EmojiUtil.wrap("❌") + " Unbekanntes Subcommand!").setEphemeral(true).queue();
    }

    private void handleAdd(SlashCommandInteraction event) {
        var roleOption = event.getOption("rolle");
        if (roleOption == null) { event.reply(EmojiUtil.wrap("❌") + " Rolle fehlt!").setEphemeral(true).queue(); return; }
        Role roleToAdd = roleOption.getAsRole();
        var filterOpt = event.getOption("filter");
        String filterRaw = filterOpt != null ? filterOpt.getAsString() : "all";
        boolean dryRun = event.getOption("dryrun") != null && Boolean.TRUE.equals(event.getOption("dryrun").getAsBoolean());

        // Permission & Hierarchie Checks
        var selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_ROLES)) {
            event.reply(EmojiUtil.wrap("⛔") + " Der Bot benötigt die Berechtigung **Rollen verwalten**!").setEphemeral(true).queue();
            return;
        }
        if (selfMember.getRoles().stream().mapToInt(Role::getPosition).max().orElse(-1) <= roleToAdd.getPosition()) {
            event.reply(EmojiUtil.wrap("⛔") + " Die Zielrolle steht höher oder gleich hoch wie die höchste Rolle des Bots.").setEphemeral(true).queue();
            return;
        }

        List<Member> members = event.getGuild().getMembers();

        List<Member> filtered = members.stream().filter(m -> {
            switch (filterRaw.toLowerCase()) {
                case "all" -> { return true; }
                case "no-role" -> { return m.getRoles().isEmpty(); }
                default -> {
                    if (filterRaw.startsWith("has:")) {
                        String id = filterRaw.substring(4).trim();
                        return m.getRoles().stream().anyMatch(r -> r.getId().equals(id));
                    }
                    return false;
                }
            }
        }).toList();

        // Entferne alle, die die Rolle bereits haben
        List<Member> targetMembers = filtered.stream().filter(m -> m.getRoles().stream().noneMatch(r -> r.getId().equals(roleToAdd.getId()))).toList();

        if (targetMembers.isEmpty()) {
            event.reply(EmojiUtil.wrap("ℹ️") + " Keine passenden Mitglieder für Filter `" + filterRaw + "` (nach Entfernen von Duplikaten). ").setEphemeral(true).queue();
            return;
        }

        if (dryRun) {
            event.reply(EmojiUtil.wrap("🧪") + " Dry-Run: **" + targetMembers.size() + "** Mitglieder würden die Rolle erhalten.").setEphemeral(true).queue();
            return;
        }

        long start = System.currentTimeMillis();
        event.reply(EmojiUtil.wrap("⚙️") + " Starte Massen-Rollenvergabe an **" + targetMembers.size() + "** Mitglieder...").queue();

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();

        EmbedBuilder progress = new EmbedBuilder();
        progress.setTitle(EmojiUtil.wrap("⚙️") + " Massen-Rollenvergabe läuft");
        progress.setColor(new Color(0x5865F2));
        progress.setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(progress.setDescription("Fortschritt: 0/" + targetMembers.size()).build()).queue(statusMsg -> {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            for (int i = 0; i < targetMembers.size(); i++) {
                Member m = targetMembers.get(i);
                int index = i;
                scheduler.schedule(() -> {
                    event.getGuild().addRoleToMember(m, roleToAdd).queue(
                            s -> {
                                int done = success.incrementAndGet();
                                int proc = processed.incrementAndGet();
                                if (proc % 25 == 0 || proc == targetMembers.size()) {
                                    statusMsg.editMessageEmbeds(progress.setDescription(
                                            "Fortschritt: " + proc + "/" + targetMembers.size() +
                                                    "\nErfolgreich: " + success.get() +
                                                    "\nFehlgeschlagen: " + failed.get()
                                    ).build()).queue();
                                }
                            },
                            e -> {
                                failed.incrementAndGet();
                                processed.incrementAndGet();
                            }
                    );

                    if (processed.get() == targetMembers.size()) {
                        scheduler.shutdown();
                        long durationMs = System.currentTimeMillis() - start;
                        EmbedBuilder finish = new EmbedBuilder();
                        finish.setTitle(EmojiUtil.wrap("✅") + " Massen-Rollenvergabe abgeschlossen");
                        finish.setColor(new Color(0x57F287));
                        finish.setTimestamp(Instant.now());
                        finish.setDescription(
                                "Mitglieder gesamt: **" + targetMembers.size() + "**\n" +
                                "Erfolgreich: **" + success.get() + "**\n" +
                                "Fehlgeschlagen: **" + failed.get() + "**\n" +
                                "Dauer: **" + (durationMs / 1000) + "s**"
                        );
                        event.getHook().sendMessageEmbeds(finish.build()).queue();
                    }
                }, index * 300L, TimeUnit.MILLISECONDS); // 300ms Abstand pro Aktion zur Entschärfung von Rate-Limits
            }
        });
    }
}
