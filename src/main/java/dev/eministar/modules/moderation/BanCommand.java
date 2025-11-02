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

import java.util.concurrent.TimeUnit;

public class BanCommand implements Command {

    @Override
    public String name() {
        return "ban";
    }

    @Override
    public String description() {
        return "Bannt einen User vom Server";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.BAN_MEMBERS)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Mitglieder bannen** Berechtigung!").queue();
            return;
        }

        if (args.length < 1) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib einen User an!\n" +
                    "**Verwendung:** `.ban <@user|id> [grund]`").queue();
            return;
        }

        String targetId = args[0].replaceAll("[<@!>]", "");
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "Kein Grund angegeben";

        event.getGuild().retrieveMemberById(targetId).queue(
                target -> {
                    if (!event.getMember().canInteract(target)) {
                        event.getMessage().reply(EmojiUtil.wrap("❌") + " Du kannst diesen User nicht bannen!").queue();
                        return;
                    }

                    event.getGuild().ban(target, 0, TimeUnit.SECONDS)
                            .reason(reason + " | Von: " + event.getAuthor().getAsTag())
                            .queue(
                                    success -> event.getMessage().reply(EmojiUtil.wrap("🔨") + " **" + target.getUser().getAsTag() + "** wurde gebannt!\n" +
                                            EmojiUtil.wrap("📝") + " **Grund:** " + reason).queue(),
                                    error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " Fehler beim Bannen!").queue()
                            );
                },
                error -> {
                    // User nicht auf Server, versuche direkt per ID zu bannen
                    event.getGuild().ban(User.fromId(targetId), 0, TimeUnit.SECONDS)
                            .reason(reason + " | Von: " + event.getAuthor().getAsTag())
                            .queue(
                                    success -> event.getMessage().reply(EmojiUtil.wrap("🔨") + " **User** (ID: " + targetId + ") wurde gebannt!\n" +
                                            EmojiUtil.wrap("📝") + " **Grund:** " + reason).queue(),
                                    error2 -> event.getMessage().reply(EmojiUtil.wrap("❌") + " User nicht gefunden oder Fehler beim Bannen!").queue()
                            );
                }
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

