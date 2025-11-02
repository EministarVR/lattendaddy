package dev.eministar.modules.counting;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;

public class CountingCommand implements Command {

    @Override
    public String name() {
        return "counting";
    }

    @Override
    public String description() {
        return "Zähl-Spiel Verwaltung";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getMessage().reply(EmojiUtil.wrap("ℹ️") + " Bitte nutze den Slash-Command `/counting`!").queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addSubcommands(
                        new SubcommandData("stats", "Zeigt die Statistiken des Zähl-Spiels"),
                        new SubcommandData("reset", "Setzt das Zähl-Spiel zurück (nur Admins)")
                );
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        String channelId = event.getChannel().getId();
        CountingGame.GameState game = CountingGame.getOrCreateGame(channelId);

        switch (subcommand) {
            case "stats" -> {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle(EmojiUtil.wrap("📊") + " Zähl-Spiel Statistiken");
                embed.setColor(new Color(0x5865F2));

                embed.addField(EmojiUtil.wrap("🔢") + " Aktuell bei", String.valueOf(game.getCurrentNumber()), true);
                embed.addField(EmojiUtil.wrap("🏆") + " Highscore", String.valueOf(game.getHighscore()), true);
                embed.addField(EmojiUtil.wrap("📈") + " Gesamt gezählt", String.valueOf(game.getTotalCounts()), true);
                embed.addField(EmojiUtil.wrap("💥") + " Gesamt Fails", String.valueOf(game.getTotalFails()), true);

                if (game.getTotalCounts() > 0) {
                    double successRate = (double) (game.getTotalCounts() - game.getTotalFails()) / game.getTotalCounts() * 100;
                    embed.addField(EmojiUtil.wrap("✅") + " Erfolgsrate", String.format("%.1f%%", successRate), true);
                }

                embed.setDescription(
                    EmojiUtil.wrap("ℹ️") + " **Regeln:**\n" +
                    "• Zähle von 1 aufwärts\n" +
                    "• Abwechselnd zählen (nicht zweimal hintereinander)\n" +
                    "• Du kannst auch rechnen: `5+3`, `10-2`, `4*2`, `8/2`\n" +
                    "• Bei Fehler: Zurück auf 1!"
                );

                embed.setTimestamp(Instant.now());
                embed.setFooter("Viel Erfolg beim Zählen! 💪", event.getJDA().getSelfUser().getAvatarUrl());

                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            }

            case "reset" -> {
                Member member = event.getMember();
                if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
                    event.reply(EmojiUtil.wrap("❌") + " Du benötigst die **Server verwalten** Berechtigung!")
                        .setEphemeral(true).queue();
                    return;
                }

                int oldHighscore = game.getHighscore();
                int oldNumber = game.getCurrentNumber() - 1;

                game.reset();
                CountingGame.updateGame(channelId, game);

                event.reply(EmojiUtil.wrap("🔄") + " **Zähl-Spiel zurückgesetzt!**\n" +
                        "Vorheriger Stand: **" + oldNumber + "**\n" +
                        "Highscore bleibt: **" + oldHighscore + "**").queue();
            }
        }
    }
}
