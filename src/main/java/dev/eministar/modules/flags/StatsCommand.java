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

public class StatsCommand implements Command {
    @Override
    public String name() { return "stats"; }

    @Override
    public String description() { return "Zeige deine Flaggenquiz-Statistiken"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!Config.getFlagQuizEnabled()) return;
        if (event.getGuild() == null) return;
        var guild = event.getGuild();
        var userId = event.getAuthor().getId();
        var ps = FlagQuizService.getPlayerStatsPublic(guild.getId(), userId);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x2B2D31));
        eb.setTitle(EmojiUtil.wrap("📊") + " Deine Flaggen-Stats");
        eb.setDescription("Punkte: **" + ps.totalPoints + "**\n" +
                "Richtig: **" + ps.correct + "** • Falsch: **" + ps.wrong + "**\n" +
                "Streak: **" + ps.currentStreak + "** (Best: " + ps.bestStreak + ")");
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addOptions(new OptionData(OptionType.USER, "user", "Andere Person anzeigen", false));
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (!Config.getFlagQuizEnabled()) { event.reply(EmojiUtil.wrap("❌") + "Flaggenquiz ist deaktiviert.").setEphemeral(true).queue(); return; }
        var guild = event.getGuild();
        if (guild == null) { event.reply(EmojiUtil.wrap("❌") + "Nur in Guilds nutzbar.").setEphemeral(true).queue(); return; }

        String userId = event.getOption("user") != null && event.getOption("user").getAsUser() != null
                ? event.getOption("user").getAsUser().getId()
                : event.getUser().getId();
        var ps = FlagQuizService.getPlayerStatsPublic(guild.getId(), userId);
        var targetUser = event.getJDA().getUserById(userId);
        String username = targetUser != null ? targetUser.getName() : userId;

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x2B2D31));
        eb.setTitle(EmojiUtil.wrap("📊") + " Stats von " + username);
        eb.setDescription("Punkte: **" + ps.totalPoints + "**\n" +
                "Richtig: **" + ps.correct + "** • Falsch: **" + ps.wrong + "**\n" +
                "Streak: **" + ps.currentStreak + "** (Best: " + ps.bestStreak + ")");
        event.replyEmbeds(eb.build()).queue();
    }
}
