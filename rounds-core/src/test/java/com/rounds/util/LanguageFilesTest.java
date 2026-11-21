package com.rounds.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the language packs shipped in the jar: the professional Russian file must
 * expose exactly the same keys as the English one, and the humorous Russian variant
 * must stay complete so that switching languages never leaves keys untranslated.
 */
class LanguageFilesTest {

    private static YamlConfiguration load(String file) {
        InputStream in = LanguageFilesTest.class.getResourceAsStream("/lang/" + file);
        assertNotNull(in, file + " must exist in resources");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Test
    void professionalRussianMatchesEnglishKeys() {
        Set<String> ru = load("RU_ru.txt").getKeys(true);
        Set<String> en = load("EN_en.txt").getKeys(true);

        Set<String> missing = new TreeSet<>(en);
        missing.removeAll(ru);
        assertTrue(missing.isEmpty(), "RU_ru is missing keys: " + missing);

        Set<String> extra = new TreeSet<>(ru);
        extra.removeAll(en);
        assertEquals(Set.of("debug.help-stats-add"), extra,
                "RU_ru should only add the legacy help-stats-add alias");
    }

    @Test
    void humorousRussianKeepsEveryEnglishKey() {
        Set<String> joke = load("RU_ru_prikol_perevod.txt").getKeys(true);
        Set<String> en = load("EN_en.txt").getKeys(true);

        Set<String> missing = new TreeSet<>(en);
        missing.removeAll(joke);
        assertTrue(missing.isEmpty(), "RU_ru_prikol_perevod is missing keys: " + missing);
    }
}
