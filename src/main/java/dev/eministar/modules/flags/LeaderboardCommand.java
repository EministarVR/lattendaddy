package dev.eministar.modules.flags;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;
import java.util.concurrent.TimeUnit;
import dev.eministar.util.EmojiUtil;

public class LeaderboardCommand implements Command {
    @Override
    public String name() { return "leaderboard"; }

    @Override
    public String description() { return "Zeigt die besten Flaggenkenner an"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!Config.getFlagQuizEnabled()) return;
        var guild = event.getGuild();
        if (guild == null) return;
        var channel = event.getChannel();
        String quizChannelId = FlagQuizService.getQuizChannelId(guild.getId());
        if (quizChannelId != null && !quizChannelId.equals(channel.getId())) {
            channel.sendMessage(EmojiUtil.wrap("❌") + "Bitte nutze den Flaggenquiz-Kanal: <#" + quizChannelId + ">").queue();
            return;
        }
        String text = FlagQuizService.leaderboardText(guild.getId(), event.getJDA(), 10);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x5865F2));
        eb.setTitle(EmojiUtil.wrap("🏆") + "Leaderboard");
        eb.setDescription(text);
        event.getChannel().sendMessageEmbeds(eb.build()).queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS, s -> {}, f -> {}));
    }

    @Override
    public CommandData getSlashCommandData() { return Commands.slash(name(), description()); }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (!Config.getFlagQuizEnabled()) { event.reply(EmojiUtil.wrap("❌") + "Flaggenquiz ist deaktiviert.").setEphemeral(true).queue(); return; }
        var guild = event.getGuild();
        if (guild == null) { event.reply(EmojiUtil.wrap("❌") + "Nur in Guilds nutzbar.").setEphemeral(true).queue(); return; }
        var channel = event.getChannel();
        String quizChannelId = FlagQuizService.getQuizChannelId(guild.getId());
        if (quizChannelId != null && !quizChannelId.equals(channel.getId())) {
            event.reply(EmojiUtil.wrap("❌") + "Bitte nutze den Flaggenquiz-Kanal: <#" + quizChannelId + ">").setEphemeral(true).queue();
            return;
        }
        String text = FlagQuizService.leaderboardText(guild.getId(), event.getJDA(), 10);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x5865F2));
        eb.setTitle(EmojiUtil.wrap("🏆") + "Leaderboard");
        eb.setDescription(text);
        event.replyEmbeds(eb.build()).queue(h -> {
            // Slash-Reply bleibt bestehen
        });
    }
}
