package dev.eministar.modules.flags;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.util.Locale;

public class FlagInfoCommand implements Command {
    @Override
    public String name() { return "flaginfo"; }

    @Override
    public String description() { return "Zeigt Statistiken zu einer Flagge (Land)"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!Config.getFlagQuizEnabled()) return;
        if (event.getGuild() == null) return;
        var guild = event.getGuild();
        if (args.length == 0) {
            event.getChannel().sendMessage(EmojiUtil.wrap("ℹ️") + " Nutzung: `" + Config.getPrefix() + "flaginfo <Land/Code>`").queue();
            return;
        }
        String query = String.join(" ", args);
        var codeOpt = FlagQuizService.resolveToCode(query);
        if (codeOpt.isEmpty()) {
            event.getChannel().sendMessage(EmojiUtil.wrap("❌") + " Konnte kein Land erkennen.").queue();
            return;
        }
        String code = codeOpt.get();
        var fs = FlagQuizService.getFlagStatsPublic(guild.getId(), code);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x2B2D31));
        eb.setTitle(EmojiUtil.wrap("🚩") + " Flagge: " + FlagQuizService.countryName(code, Locale.GERMAN) + " (" + code + ")");
        eb.setDescription("Gestellt: **" + fs.asked + "**\n" +
                "Richtig: **" + fs.correct + "** • Falsch: **" + fs.wrong + "**");
        eb.setImage("https://flagcdn.com/w512/" + code.toLowerCase(Locale.ROOT) + ".png");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addOptions(new OptionData(OptionType.STRING, "land", "Land oder Code", true));
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (!Config.getFlagQuizEnabled()) { event.reply(EmojiUtil.wrap("❌") + "Flaggenquiz ist deaktiviert.").setEphemeral(true).queue(); return; }
        var guild = event.getGuild();
        if (guild == null) { event.reply(EmojiUtil.wrap("❌") + "Nur in Guilds nutzbar.").setEphemeral(true).queue(); return; }
        var opt = event.getOption("land");
        String query = opt != null ? opt.getAsString() : "";
        var codeOpt = FlagQuizService.resolveToCode(query);
        if (codeOpt.isEmpty()) {
            event.reply(EmojiUtil.wrap("❌") + " Konnte kein Land erkennen.").setEphemeral(true).queue();
            return;
        }
        String code = codeOpt.get();
        var fs = FlagQuizService.getFlagStatsPublic(guild.getId(), code);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x2B2D31));
        eb.setTitle(EmojiUtil.wrap("🚩") + " Flagge: " + FlagQuizService.countryName(code, java.util.Locale.GERMAN) + " (" + code + ")");
        eb.setDescription("Gestellt: **" + fs.asked + "**\n" +
                "Richtig: **" + fs.correct + "** • Falsch: **" + fs.wrong + "**");
        eb.setImage("https://flagcdn.com/w512/" + code.toLowerCase(java.util.Locale.ROOT) + ".png");
        event.replyEmbeds(eb.build()).queue();
    }
}
