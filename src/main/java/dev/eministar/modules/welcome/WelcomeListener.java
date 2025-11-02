package dev.eministar.modules.welcome;

import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Member;

import java.awt.Color;

public class WelcomeListener extends ListenerAdapter {
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

        if (channel == null) return; // no channel to send to

        Member member = event.getMember();
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
