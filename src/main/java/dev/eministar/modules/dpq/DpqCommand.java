package dev.eministar.modules.dpq;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
import java.time.Instant;

public class DpqCommand implements Command {
    @Override
    public String name() { return "daily-politik-question"; }

    @Override
    public String description() { return "Steuert das DPQ System (Team)"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) { /* only slash */ }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addSubcommands(
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("send", "Postet die nächste Frage"),
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("lock", "Sperrt alle Antwort-Channels für die aktuelle Nummer"),
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("winner", "Verkündet einen Gewinner")
                                .addOptions(new OptionData(OptionType.USER, "user", "Gewinner", true))
                );
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) { event.reply(EmojiUtil.wrap("❌") + "Nur auf Servern nutzbar.").setEphemeral(true).queue(); return; }
        Member m = event.getMember();
        if (m == null || !DpqService.hasTeamPermission(m)) {
            event.reply(EmojiUtil.wrap("⛔") + " Keine Berechtigung.").setEphemeral(true).queue(); return; }

        String sub = event.getSubcommandName();
        switch (sub) {
            case "send" -> handleSend(event);
            case "lock" -> handleLock(event);
            case "winner" -> handleWinner(event);
            default -> event.reply(EmojiUtil.wrap("❌") + " Unbekannt.").setEphemeral(true).queue();
        }
    }

    private void handleSend(SlashCommandInteraction event) {
        String channelId = Config.getDpqChannelId();
        if (channelId.isEmpty()) { event.reply(EmojiUtil.wrap("❌") + " DPQ Channel nicht konfiguriert.").setEphemeral(true).queue(); return; }
        TextChannel ch = event.getGuild().getTextChannelById(channelId);
        if (ch == null) { event.reply(EmojiUtil.wrap("❌") + " Channel nicht gefunden.").setEphemeral(true).queue(); return; }
        try {
            if (DpqService.getCurrentNumber() == 0) DpqService.loadQuestions();
            var qa = DpqService.nextQuestion();
            if (qa == null) { event.reply(EmojiUtil.wrap("❌") + " Keine Fragen geladen.").setEphemeral(true).queue(); return; }
            DpqService.postQuestion(event.getGuild(), ch, qa);
            event.reply(EmojiUtil.wrap("✅") + " Frage #" + DpqService.getCurrentNumber() + " gesendet." + (qa.solution() != null ? " (Lösung intern gespeichert)" : "")).setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply(EmojiUtil.wrap("❌") + " Fehler: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleLock(SlashCommandInteraction event) {
        int number = DpqService.getCurrentNumber();
        DpqService.lockTicketsFor(event.getGuild(), number);
        event.reply(EmojiUtil.wrap("🔒") + " Antworten für #" + number + " gesperrt.").setEphemeral(true).queue();
    }

    private void handleWinner(SlashCommandInteraction event) {
        var userOpt = event.getOption("user");
        if (userOpt == null) { event.reply("User fehlt").setEphemeral(true).queue(); return; }
        var user = userOpt.getAsUser();
        String channelId = Config.getDpqChannelId();
        TextChannel ch = event.getGuild().getTextChannelById(channelId);
        if (ch == null) { event.reply(EmojiUtil.wrap("❌") + " Channel nicht gefunden.").setEphemeral(true).queue(); return; }
        int number = DpqService.getCurrentNumber();
        DpqService.announceWinner(ch, number, user);
        event.reply(EmojiUtil.wrap("🏆") + " Gewinner verkündet.").setEphemeral(true).queue();
    }
}
