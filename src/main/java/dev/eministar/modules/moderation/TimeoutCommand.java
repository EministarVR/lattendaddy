package dev.eministar.modules.moderation;

import dev.eministar.command.Command;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class TimeoutCommand implements Command {

    @Override
    public String name() {
        return "timeout";
    }

    @Override
    public String description() {
        return "Gibt einem User einen Timeout";
    }

    @Override
    public void execute(@NotNull MessageReceivedEvent event, String[] args) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Du benötigst die **Mitglieder moderieren** Berechtigung!").queue();
            return;
        }

        if (args.length < 2) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Bitte gib einen User und Dauer an!\n" +
                    "**Verwendung:** `.timeout <@user|id> <dauer> [grund]`\n" +
                    "**Beispiele:** `.timeout @user 5m`, `.timeout @user 1h`, `.timeout @user 1d`").queue();
            return;
        }

        String targetId = args[0].replaceAll("[<@!>]", "");
        String durationStr = args[1];
        String reason = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "Kein Grund angegeben";

        // Parse Duration
        Duration duration = parseDuration(durationStr);
        if (duration == null) {
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Ungültige Dauer! Nutze z.B. `5m`, `1h`, `1d`").queue();
            return;
        }

        if (duration.toMinutes() > 40320) { // Discord Max: 28 Tage
            event.getMessage().reply(EmojiUtil.wrap("❌") + " Maximale Timeout-Dauer: **28 Tage**!").queue();
            return;
        }

        event.getGuild().retrieveMemberById(targetId).queue(
                target -> {
                    if (!event.getMember().canInteract(target)) {
                        event.getMessage().reply(EmojiUtil.wrap("❌") + " Du kannst diesem User keinen Timeout geben!").queue();
                        return;
                    }

                    target.timeoutFor(duration)
                            .reason(reason + " | Von: " + event.getAuthor().getAsTag())
                            .queue(
                                    success -> event.getMessage().reply(EmojiUtil.wrap("⏱️") + " **" + target.getUser().getAsTag() + "** hat einen Timeout erhalten!\n" +
                                            EmojiUtil.wrap("⏰") + " **Dauer:** " + formatDuration(duration) + "\n" +
                                            EmojiUtil.wrap("📝") + " **Grund:** " + reason).queue(),
                                    error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " Fehler beim Timeout!").queue()
                            );
                },
                error -> event.getMessage().reply(EmojiUtil.wrap("❌") + " User nicht gefunden!").queue()
        );
    }

    private Duration parseDuration(String input) {
        try {
            char unit = input.charAt(input.length() - 1);
            int amount = Integer.parseInt(input.substring(0, input.length() - 1));

            return switch (unit) {
                case 's' -> Duration.ofSeconds(amount);
                case 'm' -> Duration.ofMinutes(amount);
                case 'h' -> Duration.ofHours(amount);
                case 'd' -> Duration.ofDays(amount);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("d ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m");

        return result.toString().trim();
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

