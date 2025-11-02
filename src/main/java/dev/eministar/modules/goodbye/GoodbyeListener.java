package dev.eministar.modules.goodbye;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.awt.Color;

public class GoodbyeListener extends ListenerAdapter {
    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        TextChannel channel = null;

        String configuredId = Config.getGoodbyeChannelId();
        if (!configuredId.isEmpty()) {
            var ch = event.getGuild().getGuildChannelById(configuredId);
            if (ch instanceof TextChannel tc) channel = tc;
        }

        if (channel == null) {
            var defaultChannel = event.getGuild().getDefaultChannel();
            if (defaultChannel instanceof TextChannel tc) channel = tc;
        }

        if (channel == null) return;

        User user = event.getUser();
        String username = user.getName();
        String avatarUrl = user.getAvatarUrl();

        int memberCount = Math.max(0, event.getGuild().getMemberCount()); // after leave

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Leb wohl, Genosse");
        eb.setDescription(EmojiUtil.wrap("🪖") + " Leb wohl, Genosse **" + username + "**. Deine Tapferkeit bleibt in Erinnerung.");
        eb.addField("Verbleibende Mitglieder", "Es bleiben **" + memberCount + "** Mitglieder", false);
        if (avatarUrl != null) eb.setThumbnail(avatarUrl);
        eb.setColor(new Color(0x8B0000)); // dunkleres rot

        channel.sendMessageEmbeds(eb.build()).queue();
    }
}
