package dev.eministar.modules.hymn;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private static final Logger logger = LoggerFactory.getLogger(TrackScheduler.class);

    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final List<String> trackPaths;
    private int currentIndex = 0;

    public TrackScheduler(AudioPlayer player, List<String> trackPaths) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
        this.trackPaths = trackPaths;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        }
    }

    public void nextTrack() {
        AudioTrack nextTrack = queue.poll();
        if (nextTrack != null) {
            player.startTrack(nextTrack, false);
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            // Zyklisch durch die Playlist gehen
            currentIndex = (currentIndex + 1) % trackPaths.size();
            logger.info("Track ended, playing next: {} (Index: {})", trackPaths.get(currentIndex), currentIndex);
        }
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    public List<String> getTrackPaths() {
        return trackPaths;
    }
}

