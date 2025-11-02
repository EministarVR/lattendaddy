package dev.eministar.modules.flags;

import dev.eministar.command.Command;
import dev.eministar.config.Config;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import dev.eministar.util.EmojiUtil;

public class FlagSetupCommand implements Command {
    @Override
    public String name() { return "flagsetup"; }

    @Override
    public String description() { return "Setzt den Flaggenquiz-Kanal und erstellt das Dashboard (Admin)"; }

    @Override
    public void execute(MessageReceivedEvent event, String[] args) {
        if (!Config.getFlagQuizEnabled()) return;
        var member = event.getMember();
        var guild = event.getGuild();
        var channel = event.getChannel();
        if (member == null || guild == null) return;
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            channel.sendMessage(EmojiUtil.wrap("❌") + "Du benötigst die Berechtigung 'Server verwalten'.").queue();
            return;
        }
        // aktuellen Kanal binden
        FlagQuizService.setQuizChannel(guild.getId(), channel.getId());
        channel.sendMessage(EmojiUtil.wrap("✅") + "Flaggenquiz ist nun in diesem Kanal aktiv.").queue(m -> m.delete().queueAfter(10, java.util.concurrent.TimeUnit.SECONDS, s -> {}, f -> {}));
        // Dashboard erzeugen/aktualisieren
        if (channel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel tc) {
            FlagQuizService.tryEnsureDashboardMessage(guild.getId(), tc);
        }
    }

    @Override
    public CommandData getSlashCommandData() { return Commands.slash(name(), description()); }

    @Override
    public void executeSlash(SlashCommandInteraction event) {
        if (!Config.getFlagQuizEnabled()) { event.reply(EmojiUtil.wrap("❌") + "Flaggenquiz ist deaktiviert.").setEphemeral(true).queue(); return; }
        var member = event.getMember();
        var guild = event.getGuild();
        var channel = event.getChannel();
        if (member == null || guild == null) { event.reply(EmojiUtil.wrap("❌") + "Nur in Guilds nutzbar.").setEphemeral(true).queue(); return; }
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(EmojiUtil.wrap("❌") + "Du benötigst die Berechtigung 'Server verwalten'.").setEphemeral(true).queue();
            return;
        }
        FlagQuizService.setQuizChannel(guild.getId(), channel.getId());
        if (channel instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel tc) {
            FlagQuizService.tryEnsureDashboardMessage(guild.getId(), tc);
        }
        event.reply(EmojiUtil.wrap("✅") + "Flaggenquiz ist nun in diesem Kanal aktiv. Dashboard erstellt/aktualisiert.").setEphemeral(true).queue();
    }
}
