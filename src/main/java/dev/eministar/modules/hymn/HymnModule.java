package dev.eministar.modules.hymn;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.eministar.config.Config;
import dev.eministar.util.EmojiUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HymnModule extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(HymnModule.class);

    private final AudioPlayerManager playerManager;
    private AudioPlayer audioPlayer;
    private TrackScheduler trackScheduler;
    private AudioManager audioManager;

    private boolean enabled;
    private String hymnChannelId;
    private final List<String> hymns;
    private boolean isPlaying = false;

    public HymnModule() {
        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerLocalSource(playerManager);

        this.hymns = new ArrayList<>();
        loadConfig();
        loadHymns();
    }

    private void loadConfig() {
        enabled = Config.getHymnEnabled();
        hymnChannelId = Config.getHymnChannelId();
        logger.info("Hymn Module - Enabled: {}, Channel: {}", enabled, hymnChannelId);
    }

    private void loadHymns() {
        // Lade alle music*.mp3 Dateien aus resources
        for (int i = 1; i <= 5; i++) {
            String filename = i == 1 ? "music.mp3" : "music" + i + ".mp3";
            File file = new File("src/main/resources/" + filename);

            if (file.exists()) {
                hymns.add(file.getAbsolutePath());
                logger.info("Loaded hymn: {}", filename);
            } else {
                // Versuche auch im compiled resources Ordner
                file = new File("resources/" + filename);
                if (file.exists()) {
                    hymns.add(file.getAbsolutePath());
                    logger.info("Loaded hymn from compiled resources: {}", filename);
                }
            }
        }

        if (hymns.isEmpty()) {
            logger.warn("No hymns found! Place music.mp3, music2.mp3, music3.mp3, music4.mp3, music5.mp3 in resources folder");
        } else {
            logger.info("Loaded {} hymns total", hymns.size());
        }
    }

    public void startPlaying(Guild guild) {
        if (!enabled || hymnChannelId.isEmpty() || hymns.isEmpty()) {
            logger.warn("Cannot start playing - module disabled, no channel, or no hymns");
            return;
        }

        VoiceChannel channel = guild.getVoiceChannelById(hymnChannelId);
        if (channel == null) {
            logger.error("Hymn channel not found: {}", hymnChannelId);
            return;
        }

        // Erstelle Audio Player
        audioPlayer = playerManager.createPlayer();
        trackScheduler = new TrackScheduler(audioPlayer, hymns);
        audioPlayer.addListener(trackScheduler);

        // Verbinde zum Voice Channel
        audioManager = guild.getAudioManager();
        audioManager.openAudioConnection(channel);
        audioManager.setSendingHandler(new AudioPlayerSendHandler(audioPlayer));

        // Starte mit erstem Lied
        playNextTrack();
        isPlaying = true;

        logger.info("Started playing hymns in channel: {}", channel.getName());
    }

    private void playNextTrack() {
        if (hymns.isEmpty()) return;

        int index = trackScheduler.getCurrentIndex();
        String trackPath = hymns.get(index);

        playerManager.loadItem(trackPath, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                audioPlayer.playTrack(track);
                logger.info("Now playing: {} (Track {}/{})", trackPath, index + 1, hymns.size());
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                // Nicht relevant für lokale Dateien
            }

            @Override
            public void noMatches() {
                logger.error("No matches found for: {}", trackPath);
                skipToNext();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                logger.error("Failed to load track: {}", trackPath, exception);
                skipToNext();
            }
        });
    }

    private void skipToNext() {
        trackScheduler.setCurrentIndex((trackScheduler.getCurrentIndex() + 1) % hymns.size());
        playNextTrack();
    }

    public void skip() {
        if (!isPlaying || audioPlayer == null) {
            return;
        }

        logger.info("Skipping current track");
        audioPlayer.stopTrack();
        skipToNext();
    }

    public void stop() {
        if (audioManager != null) {
            audioManager.closeAudioConnection();
        }
        if (audioPlayer != null) {
            audioPlayer.destroy();
        }
        isPlaying = false;
        logger.info("Stopped playing hymns");
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!enabled || event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw();

        if (content.equalsIgnoreCase(".skip")) {
            skip();
            event.getMessage().reply(EmojiUtil.wrap("⏭️") + " **Track übersprungen!**").queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("hymn")) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "start" -> {
                if (isPlaying) {
                    event.reply(EmojiUtil.wrap("ℹ️") + " Hymnen spielen bereits!").setEphemeral(true).queue();
                    return;
                }

                Guild guild = event.getGuild();
                if (guild == null) {
                    event.reply(EmojiUtil.wrap("❌") + " Nur auf Servern verfügbar!").setEphemeral(true).queue();
                    return;
                }

                startPlaying(guild);
                event.reply(EmojiUtil.wrap("🎵") + " **Hymnen werden jetzt gespielt!**\n" +
                        "Nutze `.skip` um ein Lied zu überspringen.").queue();
            }

            case "stop" -> {
                if (!isPlaying) {
                    event.reply(EmojiUtil.wrap("ℹ️") + " Es spielen keine Hymnen!").setEphemeral(true).queue();
                    return;
                }

                stop();
                event.reply(EmojiUtil.wrap("⏹️") + " **Hymnen gestoppt!**").queue();
            }

            case "skip" -> {
                if (!isPlaying) {
                    event.reply(EmojiUtil.wrap("ℹ️") + " Es spielen keine Hymnen!").setEphemeral(true).queue();
                    return;
                }

                skip();
                event.reply(EmojiUtil.wrap("⏭️") + " **Track übersprungen!**").queue();
            }

            case "status" -> {
                if (!isPlaying) {
                    event.reply(EmojiUtil.wrap("⏹️") + " **Status:** Gestoppt").setEphemeral(true).queue();
                    return;
                }

                int current = trackScheduler.getCurrentIndex() + 1;
                int total = hymns.size();
                event.reply(EmojiUtil.wrap("🎵") + " **Status:** Spielt\n" +
                        "**Track:** " + current + "/" + total + "\n" +
                        "**Loop:** Endlos").setEphemeral(true).queue();
            }
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}

