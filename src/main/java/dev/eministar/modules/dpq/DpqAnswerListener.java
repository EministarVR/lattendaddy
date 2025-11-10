package dev.eministar.modules.dpq;

import dev.eministar.config.Config;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class DpqAnswerListener extends ListenerAdapter {
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getGuild() == null) { event.reply("Nur im Server nutzbar.").setEphemeral(true).queue(); return; }
        String id = event.getComponentId();
        if (id.startsWith("dpq_answer_")) {
            String channelId = Config.getDpqChannelId();
            if (channelId.isEmpty() || !channelId.equals(event.getChannel().getId())) {
                event.reply("Falscher Channel für DPQ.").setEphemeral(true).queue(); return; }
            int number = Integer.parseInt(id.substring("dpq_answer_".length()));
            var member = event.getMember();
            if (member == null) { event.reply("Kein Member.").setEphemeral(true).queue(); return; }
            DpqService.handleAnswerClick(event.getGuild(), member, number);
            event.reply("Ticket erstellt – viel Erfolg!").setEphemeral(true).queue();
            return;
        }
        if ("dpq_mute_ping".equals(id)) {
            var member = event.getMember();
            if (member == null) { event.reply("Kein Member").setEphemeral(true).queue(); return; }
            String pingRoleId = Config.getDpqPingRoleId();
            if (pingRoleId == null || pingRoleId.isEmpty()) { event.reply("Keine Ping-Rolle konfiguriert.").setEphemeral(true).queue(); return; }
            var role = event.getGuild().getRoleById(pingRoleId);
            if (role == null) { event.reply("Rolle nicht gefunden.").setEphemeral(true).queue(); return; }
            if (!member.getRoles().contains(role)) { event.reply("Du hast die Ping-Rolle bereits nicht.").setEphemeral(true).queue(); return; }
            event.getGuild().removeRoleFromMember(member, role).queue(
                    s -> event.reply("✅ Ping deaktiviert. Du wirst für zukünftige Fragen nicht mehr gepingt.").setEphemeral(true).queue(),
                    e -> event.reply("❌ Entfernen der Rolle fehlgeschlagen.").setEphemeral(true).queue()
            );
        }
    }
}
