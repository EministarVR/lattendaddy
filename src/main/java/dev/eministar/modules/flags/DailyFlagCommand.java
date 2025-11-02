package dev.eministar.modules.flags;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import dev.eministar.util.EmojiUtil;

public class DailyFlagCommand implements Command {
    @Override
    public String name() { return "dailyflag"; }

    @Override
    public String description() { return "Tägliche Flaggen-Challenge mit Extra-Punkten (1x/Tag)"; }

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

        var user = event.getAuthor();
        if (!FlagQuizService.canDoDaily(guild.getId(), user.getId())) {
            channel.sendMessage(EmojiUtil.wrap("❌") + "Du hast die tägliche Challenge heute bereits gespielt.").queue();
            return;
        }
        FlagQuizService.startRound(guild.getId(), channel.getId(), channel, FlagQuizService.Mode.DAILY, user.getId());
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

        var user = event.getUser();
        if (!FlagQuizService.canDoDaily(guild.getId(), user.getId())) {
            event.reply(EmojiUtil.wrap("❌") + "Du hast die tägliche Challenge heute bereits gespielt.").setEphemeral(true).queue();
            return;
        }
        if (FlagQuizService.startRound(guild.getId(), channel.getId(), channel, FlagQuizService.Mode.DAILY, user.getId())) {
            event.reply(EmojiUtil.wrap("✅") + "Daily gestartet! — nur deine Antworten zählen!").setEphemeral(true).queue();
        } else {
            event.reply(EmojiUtil.wrap("ℹ️") + "Für dich läuft hier bereits eine Runde.").setEphemeral(true).queue();
        }
    }
}
