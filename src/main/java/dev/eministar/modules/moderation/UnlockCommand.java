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

public class UnlockCommand implements Command {

    @Override
    public String name() {
        return "unlock";
    }

    @Override
    public String description() {
        return "Entsperrt den Channel komplett (ursprüngliche Einstellungen)";
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
                .removePermissionOverride(event.getGuild().getPublicRole())
                .queue(
                        success -> event.getMessage().reply(EmojiUtil.wrap("🔓") + " **Channel entsperrt!** Ursprüngliche Einstellungen wiederhergestellt.").queue(),
                        error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " Fehler beim Entsperren!").queue()
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

