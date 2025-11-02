package dev.eministar.modules.ping;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;

public class PingCommand implements Command {
    @Override
    public String name() {
        return "ping";
    }

    @Override
    public String description() {
        return "Antwortet mit Pong und Latenz";
    }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        long ping = event.getJDA().getGatewayPing();
        event.getChannel().sendMessage(EmojiUtil.wrap("\uD83C\uDFD3") + "Pong! Ping: " + ping + "ms").queue();
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (event.isAcknowledged()) return;
        long ping = event.getJDA().getGatewayPing();
        event.reply(EmojiUtil.wrap("\uD83C\uDFD3") + "Pong! Ping: " + ping + "ms").queue();
    }
}
