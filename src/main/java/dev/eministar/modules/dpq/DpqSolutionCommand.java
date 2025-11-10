package dev.eministar.modules.dpq;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.time.Instant;
import java.util.regex.Pattern;

public class DpqSolutionCommand implements Command {
    @Override
    public String name() { return "lösungen"; }

    @Override
    public String description() { return "Sendet die Musterlösung an alle offenen DPQ Tickets"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) { }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addSubcommands(new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("senden", "Musterlösung posten")
                        .addOptions(new OptionData(OptionType.STRING, "text", "Lösungstext (leer = auto)", false)));
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.getGuild() == null) { event.reply(EmojiUtil.wrap("❌") + "Nur auf Servern").setEphemeral(true).queue(); return; }
        Member m = event.getMember();
        if (m == null || !DpqService.hasTeamPermission(m)) { event.reply(EmojiUtil.wrap("⛔") + "Keine Rechte").setEphemeral(true).queue(); return; }
        String sub = event.getSubcommandName();
        if (!"senden".equals(sub)) { event.reply(EmojiUtil.wrap("❌") + "Unbekannt").setEphemeral(true).queue(); return; }
        var opt = event.getOption("text");
        int number = DpqService.getCurrentNumber();
        String text = opt != null ? opt.getAsString() : DpqService.getSolutionForNumber(number);
        if (text == null || text.isBlank()) {
            event.reply(EmojiUtil.wrap("❌") + " Keine Lösung gefunden (weder Parameter noch gespeichert). Nutze /lösungen senden text:<...>").setEphemeral(true).queue();
            return;
        }
        if (text.length() > 1900) { event.reply("Zu lang (1900 max)").setEphemeral(true).queue(); return; }

        int sent = 0;
        for (var entry : java.util.List.copyOf(event.getGuild().getTextChannels())) {
            if (entry.getName().startsWith("dpq-" + number + "-")) {
                sendSolutionEmbed(entry, number, text, m);
                sent++;
            }
        }
        event.reply(EmojiUtil.wrap("✅") + " Lösung an " + sent + " Tickets gesendet.").setEphemeral(true).queue();
    }

    private void sendSolutionEmbed(TextChannel ch, int number, String text, Member sender) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x5865F2));
        eb.setTitle("📘 DPQ #" + number + " Musterlösung");
        eb.setDescription(text + "\n\n" + EmojiUtil.wrap("ℹ️") + " Dies ist eine mögliche Lösung. Es gibt viele korrekte Perspektiven – politische Bildung lebt von Vielfalt.");
        eb.setFooter("Gesendet von " + sender.getEffectiveName(), sender.getUser().getAvatarUrl());
        eb.setTimestamp(Instant.now());
        ch.sendMessageEmbeds(eb.build()).queue();
    }
}
