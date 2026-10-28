package com.rounds.music;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoundMusicManagerTest {

    @Test
    void testPlaylistInitialization() {
        RoundMusicManager mm = new RoundMusicManager(null);
        List<RoundMusicManager.Track> playlist = mm.getPlaylist();

        assertNotNull(playlist);
        assertEquals(4, playlist.size());

        assertEquals("music1", playlist.get(0).getName());
        assertEquals("rounds:music1", playlist.get(0).getSoundKey());
        assertEquals(6525L, playlist.get(0).getDurationTicks());

        assertEquals("music2", playlist.get(1).getName());
        assertEquals("rounds:music2", playlist.get(1).getSoundKey());
        assertEquals(3187L, playlist.get(1).getDurationTicks());

        assertEquals("music3", playlist.get(2).getName());
        assertEquals("rounds:music3", playlist.get(2).getSoundKey());
        assertEquals(5626L, playlist.get(2).getDurationTicks());

        assertEquals("music4", playlist.get(3).getName());
        assertEquals("rounds:music4", playlist.get(3).getSoundKey());
        assertEquals(6138L, playlist.get(3).getDurationTicks());
    }

    @Test
    void testSequentialRoundProgressionWithoutRepetitionUntilEnd() {
        RoundMusicManager mm = new RoundMusicManager(null);

        // Round 1 -> music1
        mm.onRoundStart();
        assertEquals("music1", mm.getCurrentTrack().getName());
        assertEquals(1, mm.getCurrentIndex());

        // Round 2 -> music2
        mm.onRoundStart();
        assertEquals("music2", mm.getCurrentTrack().getName());
        assertEquals(2, mm.getCurrentIndex());

        // Round 3 -> music3
        mm.onRoundStart();
        assertEquals("music3", mm.getCurrentTrack().getName());
        assertEquals(3, mm.getCurrentIndex());

        // Round 4 -> music4
        mm.onRoundStart();
        assertEquals("music4", mm.getCurrentTrack().getName());
        // Reached the end of the list, index loops back to 0
        assertEquals(0, mm.getCurrentIndex());

        // Round 5 -> music1 (only repeats after reaching the end of the list)
        mm.onRoundStart();
        assertEquals("music1", mm.getCurrentTrack().getName());
        assertEquals(1, mm.getCurrentIndex());

        // Round 6 -> music2
        mm.onRoundStart();
        assertEquals("music2", mm.getCurrentTrack().getName());
        assertEquals(2, mm.getCurrentIndex());
    }

    @Test
    void testResetPlaylist() {
        RoundMusicManager mm = new RoundMusicManager(null);

        mm.onRoundStart(); // music1 -> next is music2 (index 1)
        mm.onRoundStart(); // music2 -> next is music3 (index 2)
        assertEquals("music2", mm.getCurrentTrack().getName());
        assertEquals(2, mm.getCurrentIndex());

        mm.resetPlaylist();
        assertNull(mm.getCurrentTrack());
        assertEquals(0, mm.getCurrentIndex());

        mm.onRoundStart();
        assertEquals("music1", mm.getCurrentTrack().getName());
        assertEquals(1, mm.getCurrentIndex());
    }

    @Test
    void testNextTrack() {
        RoundMusicManager mm = new RoundMusicManager(null);

        RoundMusicManager.Track track1 = mm.nextTrack();
        assertEquals("music1", track1.getName());

        RoundMusicManager.Track track2 = mm.nextTrack();
        assertEquals("music2", track2.getName());

        RoundMusicManager.Track track3 = mm.nextTrack();
        assertEquals("music3", track3.getName());

        RoundMusicManager.Track track4 = mm.nextTrack();
        assertEquals("music4", track4.getName());

        RoundMusicManager.Track trackRepeat = mm.nextTrack();
        assertEquals("music1", trackRepeat.getName());
    }
}
