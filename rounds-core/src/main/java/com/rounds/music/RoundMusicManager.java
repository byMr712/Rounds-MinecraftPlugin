package com.rounds.music;

import com.rounds.RoundsPlugin;
import com.rounds.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class RoundMusicManager implements Listener {

    public static final class Track {
        private final String name;
        private final String soundKey;
        private final long durationTicks;

        public Track(String name, String soundKey, long durationTicks) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.soundKey = Objects.requireNonNull(soundKey, "soundKey cannot be null");
            this.durationTicks = Math.max(20L, durationTicks);
        }

        public String getName() {
            return name;
        }

        public String getSoundKey() {
            return soundKey;
        }

        public long getDurationTicks() {
            return durationTicks;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Track track = (Track) o;
            return soundKey.equals(track.soundKey);
        }

        @Override
        public int hashCode() {
            return soundKey.hashCode();
        }

        @Override
        public String toString() {
            return name + " (" + soundKey + ", " + (durationTicks / 20) + "s)";
        }
    }

    private final RoundsPlugin plugin;
    private final List<Track> playlist = new ArrayList<>();
    private int currentIndex = 0;
    private Track currentTrack = null;
    private int loopTaskId = -1;

    public RoundMusicManager(RoundsPlugin plugin) {
        this.plugin = plugin;
        initDefaultPlaylist();
    }

    private void initDefaultPlaylist() {
        // Durations parsed from original_mod/sounds Ogg Vorbis streams (rate 48000 Hz):
        // music1.ogg: 326.25s -> 6525 ticks
        // music2.ogg: 159.34s -> 3187 ticks
        // music3.ogg: 281.28s -> 5626 ticks
        // music4.ogg: 306.88s -> 6138 ticks
        playlist.clear();
        playlist.add(new Track("music1", "rounds:music1", 6525L));
        playlist.add(new Track("music2", "rounds:music2", 3187L));
        playlist.add(new Track("music3", "rounds:music3", 5626L));
        playlist.add(new Track("music4", "rounds:music4", 6138L));
        currentIndex = 0;
    }

    public synchronized void onRoundStart() {
        stop();
        if (!isEnabled() || playlist.isEmpty()) return;

        // Pick current track, then advance index without repeating until reached end of list
        Track track = playlist.get(currentIndex);
        currentTrack = track;
        currentIndex = (currentIndex + 1) % playlist.size();

        if (plugin != null) {
            plugin.getLogger().info("[Music] Round start: playing " + track.getName() + " (" + track.getSoundKey() + ", next index=" + currentIndex + ")");
            playCurrentTrack();
            startLoopTask(track);
        }
    }

    public synchronized void playCurrentTrack() {
        if (currentTrack == null || Bukkit.getServer() == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            playTrackForPlayer(p, currentTrack);
        }
    }

    public void playTrackForPlayer(Player player, Track track) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getBotManager() != null && plugin.getBotManager().isBot(player)) return;

        float volume = (float) getVolume();
        try {
            // Paper entity-bound sound packet: follows player, non-directional, no distance fade
            player.playSound(player, track.getSoundKey(), SoundCategory.RECORDS, volume, 1.0f);
        } catch (Throwable t) {
            try {
                player.playSound(player.getLocation(), track.getSoundKey(), SoundCategory.RECORDS, volume, 1.0f);
            } catch (Throwable ignored) {}
        }
    }

    public void stopPlayerMusic(Player player, Track track) {
        if (player == null || !player.isOnline()) return;
        try {
            if (track != null) {
                player.stopSound(track.getSoundKey(), SoundCategory.RECORDS);
                player.stopSound(track.getSoundKey(), SoundCategory.MUSIC);
            }
            for (Track t : playlist) {
                player.stopSound(t.getSoundKey(), SoundCategory.RECORDS);
                player.stopSound(t.getSoundKey(), SoundCategory.MUSIC);
            }
            player.stopSound(SoundCategory.RECORDS);
            player.stopSound(SoundCategory.MUSIC);
        } catch (Throwable ignored) {}
    }

    private synchronized void startLoopTask(Track trackToLoop) {
        if (loopTaskId != -1) {
            Bukkit.getScheduler().cancelTask(loopTaskId);
            loopTaskId = -1;
        }

        long delayTicks = Math.max(20L, trackToLoop.getDurationTicks());
        loopTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (plugin.getGameManager() == null || plugin.getGameManager().getState() != GameManager.GameState.PLAYING) {
                stop();
                return;
            }
            if (currentTrack != null && currentTrack.equals(trackToLoop)) {
                if (plugin != null) {
                    plugin.getLogger().info("[Music] Looping track: " + trackToLoop.getName());
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playTrackForPlayer(p, trackToLoop);
                }
            }
        }, delayTicks, delayTicks);
    }

    public synchronized void stop() {
        if (loopTaskId != -1) {
            if (Bukkit.getServer() != null) {
                Bukkit.getScheduler().cancelTask(loopTaskId);
            }
            loopTaskId = -1;
        }
        if (currentTrack != null) {
            if (plugin != null) {
                plugin.getLogger().info("[Music] Stopped music: " + currentTrack.getName());
            }
            if (Bukkit.getServer() != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    stopPlayerMusic(p, currentTrack);
                }
            }
            currentTrack = null;
        }
    }

    public synchronized void resetPlaylist() {
        stop();
        currentIndex = 0;
    }

    public synchronized Track nextTrack() {
        stop();
        if (playlist.isEmpty()) return null;
        Track track = playlist.get(currentIndex);
        currentTrack = track;
        currentIndex = (currentIndex + 1) % playlist.size();
        if (plugin != null) {
            plugin.getLogger().info("[Music] Round start: playing " + track.getName() + " (" + track.getSoundKey() + ", next index=" + currentIndex + ")");
            playCurrentTrack();
            startLoopTask(track);
        }
        return track;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            event.getPlayer().stopSound(SoundCategory.RECORDS);
            event.getPlayer().stopSound(SoundCategory.MUSIC);
        } catch (Throwable ignored) {}
        if (currentTrack != null && plugin.getGameManager() != null
                && plugin.getGameManager().getState() == GameManager.GameState.PLAYING) {
            playTrackForPlayer(event.getPlayer(), currentTrack);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopPlayerMusic(event.getPlayer(), currentTrack);
    }

    @EventHandler
    public void onGameModeChange(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == org.bukkit.GameMode.SPECTATOR) {
            if (currentTrack != null && plugin.getGameManager() != null
                    && plugin.getGameManager().getState() == GameManager.GameState.PLAYING) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (event.getPlayer().isOnline() && currentTrack != null) {
                        playTrackForPlayer(event.getPlayer(), currentTrack);
                    }
                }, 2L);
            }
        }
    }

    public boolean isEnabled() {
        return plugin == null || (plugin.getRoundsConfig() != null && plugin.getRoundsConfig().isMusicEnabled());
    }

    public double getVolume() {
        return plugin.getRoundsConfig() != null ? plugin.getRoundsConfig().getMusicVolume() : 0.4;
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public List<Track> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    public boolean isPlaying() {
        return currentTrack != null;
    }
}
