package dev.eministar.modules.welcome;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;

public class WelcomeListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(WelcomeListener.class);

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        TextChannel channel = null;

        String configuredId = Config.getWelcomeChannelId();
        if (!configuredId.isEmpty()) {
            var ch = event.getGuild().getGuildChannelById(configuredId);
            if (ch instanceof TextChannel tc) channel = tc;
        }

        if (channel == null) {
            var defaultChannel = event.getGuild().getDefaultChannel();
            if (defaultChannel instanceof TextChannel tc) channel = tc;
        }

        Member member = event.getMember();

        // Rolle(n) automatisch vergeben
        List<String> joinRoles = Config.getJoinRoleIds();
        if (!joinRoles.isEmpty()) {
            for (String roleId : joinRoles) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    event.getGuild().addRoleToMember(member, role).queue(
                        s -> logger.debug("Assigned join role {} to member {}", role.getId(), member.getId()),
                        e -> logger.warn("Could not assign join role {} to member {}", roleId, member.getId())
                    );
                }
            }
        }

        if (channel == null) return; // no channel to send to

        String username = member.getEffectiveName();
        String avatarUrl = member.getUser().getAvatarUrl();

        int memberCount = event.getGuild().getMemberCount(); // includes the new member

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Willkommen, Genosse!");
        eb.setDescription(EmojiUtil.wrap("🚩") + " Salut, Genosse **" + username + "**! Willkommen im Kollektiv.");
        eb.addField("Mitgliedsnummer", "Du bist das **" + memberCount + "**. Mitglied", false);
        if (avatarUrl != null) eb.setThumbnail(avatarUrl);
        eb.setColor(new Color(0xC41E3A)); // kommunistisches rot

        channel.sendMessageEmbeds(eb.build()).queue();
    }
}
