package dev.eministar.modules.moderation;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class LockCommand implements Command {

    @Override
    public String name() {
        return "lock";
    }

    @Override
    public String description() {
        return "Macht den Channel privat (niemand kann sehen/schreiben)";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_CHANNEL)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Kanäle verwalten** Berechtigung!").queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();

        channel.getManager()
                .putPermissionOverride(
                        event.getGuild().getPublicRole(),
                        null,
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
                )
                .queue(
                        success -> event.getMessage().reply(EmojiUtil.wrap("🔒") + " **Channel gesperrt!** Niemand kann mehr sehen oder schreiben.").queue(),
                        error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " Fehler beim Sperren des Channels!").queue()
                );
    }

    @Override
    public CommandData getSlashCommandData() {
        return null;
    }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        // Nicht verwendet
    }
}

