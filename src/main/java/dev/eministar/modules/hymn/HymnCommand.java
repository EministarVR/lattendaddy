package dev.eministar.modules.hymn;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

public class HymnCommand implements Command {

    @Override
    public String name() {
        return "hymn";
    }

    @Override
    public String description() {
        return "Spielt Hymnen im Voice-Channel ab";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        event.getMessage().reply(EmojiUtil.wrap("ℹ️") + " Bitte nutze den Slash-Command `/hymn`!").queue();
    }

    @Override
    public CommandData getSlashCommandData() {
        return Commands.slash(name(), description())
                .addSubcommands(
                        new SubcommandData("start", "Startet das Abspielen der Hymnen"),
                        new SubcommandData("stop", "Stoppt das Abspielen der Hymnen"),
                        new SubcommandData("skip", "Überspringt das aktuelle Lied"),
                        new SubcommandData("status", "Zeigt den Status der Hymnen")
                );
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        // Die HymnModule-Klasse handelt die Slash-Commands direkt
    }
}

