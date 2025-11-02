package dev.eministar.modules.flags;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import dev.eministar.util.EmojiUtil;

public class FlaggeCommand implements Command {
    @Override
    public String name() { return "flagge"; }

    @Override
    public String description() { return "Starte ein neues Flaggenrätsel (optional: easy)"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!Config.getFlagQuizEnabled()) return;
        var guild = event.getGuild();
        if (guild == null) return;
        var channel = event.getChannel();

        // Channel-Bindung
        String quizChannelId = FlagQuizService.getQuizChannelId(guild.getId());
        if (quizChannelId != null && !quizChannelId.equals(channel.getId())) {
            channel.sendMessage(EmojiUtil.wrap("❌") + "Bitte nutze den Flaggenquiz-Kanal: <#" + quizChannelId + ">").queue();
            return;
        }

        FlagQuizService.Mode mode = FlagQuizService.Mode.NORMAL;
        if (args.length > 0 && args[0].equalsIgnoreCase("easy")) {
            mode = FlagQuizService.Mode.EASY;
        }
        String targetUserId = event.getAuthor().getId();
        FlagQuizService.startRound(guild.getId(), channel.getId(), channel, mode, targetUserId);
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addOptions(new OptionData(OptionType.STRING, "mode", "Modus: normal/easy", false)
                        .addChoice("normal", "normal")
                        .addChoice("easy", "easy"));
    }

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

        String modeOpt = event.getOption("mode") != null ? event.getOption("mode").getAsString() : "normal";
        FlagQuizService.Mode mode = modeOpt.equalsIgnoreCase("easy") ? FlagQuizService.Mode.EASY : FlagQuizService.Mode.NORMAL;
        String targetUserId = event.getUser().getId();
        if (FlagQuizService.startRound(guild.getId(), channel.getId(), channel, mode, targetUserId)) {
            event.reply(EmojiUtil.wrap("✅") + "Runde gestartet – nur deine Antworten zählen!").setEphemeral(true).queue();
        } else {
            event.reply(EmojiUtil.wrap("ℹ️") + "Für dich läuft hier bereits eine Runde.").setEphemeral(true).queue();
        }
    }
}
