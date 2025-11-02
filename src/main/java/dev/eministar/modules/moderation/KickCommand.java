package dev.eministar.modules.moderation;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

public class KickCommand implements Command {

    @Override
    public String name() {
        return "kick";
    }

    @Override
    public String description() {
        return "Kickt einen User vom Server";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.KICK_MEMBERS)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Mitglieder kicken** Berechtigung!").queue();
            return;
        }

        if (args.length < 1) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib einen User an!\n" +
                    "**Verwendung:** `.kick <@user|id> [grund]`").queue();
            return;
        }

        String targetId = args[0].replaceAll("[<@!>]", "");
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kein Grund angegeben";

        event.getGuild().retrieveMemberById(targetId).queue(
                target -> {
                    if (!event.getMember().canInteract(target)) {
                        event.getMessage().reply(EmojiUtil.wrap("❌") + " Du kannst diesen User nicht kicken!").queue();
                        return;
                    }

                    event.getGuild().kick(target)
                            .reason(reason + " | Von: " + event.getAuthor().getAsTag())
                            .queue(
                                    success -> event.getMessage().reply(EmojiUtil.wrap("👢") + " **" + target.getUser().getAsTag() + "** wurde gekickt!\n" +
                                            EmojiUtil.wrap("📝") + " **Grund:** " + reason).queue(),
                                    error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " Fehler beim Kicken!").queue()
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

