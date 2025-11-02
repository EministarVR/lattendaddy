package dev.eministar.command;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

public interface Command {
    String name();
    String description();

    // prefix command
    void execute(MessageReceivedEvent event, String[] args);

    // slash command
    default void executeSlash(SlashCommandInteraction event) {
        // optional
    }

    // slash command data (for options, etc.)
    default CommandData getSlashCommandData() {
        return null; // override for custom options
    }
}
