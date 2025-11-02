package dev.eministar.modules.moderation;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

public class UntimeoutCommand implements Command {

    @Override
    public String name() {
        return "untimeout";
    }

    @Override
    public String description() {
        return "Entfernt den Timeout von einem User";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Mitglieder moderieren** Berechtigung!").queue();
            return;
        }

        if (args.length < 1) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib einen User an!\n" +
                    "**Verwendung:** `.untimeout <@user|id>`").queue();
            return;
        }

        String targetId = args[0].replaceAll("[<@!>]", "");

        event.getGuild().retrieveMemberById(targetId).queue(
                target -> {
                    if (target.getTimeOutEnd() == null) {
                        event.getMessage().reply(EmojiUtil.wrap("ℹ️") + " **" + target.getUser().getAsTag() + "** hat keinen Timeout!").queue();
                        return;
                    }

                    target.removeTimeout()
                            .reason("Timeout entfernt von: " + event.getAuthor().getAsTag())
                            .queue(
                                    success -> event.getMessage().reply(EmojiUtil.wrap("✅") + " Timeout von **" + target.getUser().getAsTag() + "** wurde entfernt!").queue(),
                                    error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " Fehler beim Entfernen des Timeouts!").queue()
                            );
                },
                error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " User nicht gefunden!").queue()
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

