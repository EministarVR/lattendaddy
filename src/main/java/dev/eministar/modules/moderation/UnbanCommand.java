package dev.eministar.modules.moderation;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

public class UnbanCommand implements Command {

    @Override
    public String name() {
        return "unban";
    }

    @Override
    public String description() {
        return "Entbannt einen User";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.BAN_MEMBERS)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Mitglieder bannen** Berechtigung!").queue();
            return;
        }

        if (args.length < 1) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib eine User-ID an!\n" +
                    "**Verwendung:** `.unban <id>`").queue();
            return;
        }

        String userId = args[0].replaceAll("[<@!>]", "");

        event.getGuild().unban(User.fromId(userId))
                .queue(
                        success -> event.getMessage().reply(EmojiUtil.wrap("✅") + " **User** (ID: " + userId + ") wurde entbannt!").queue(),
                        error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " User ist nicht gebannt oder Fehler!").queue()
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

