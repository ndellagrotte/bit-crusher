package com.example.bitcrusher.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SoundWhitelistTest {

    @Test
    void exactIds() {
        SoundWhitelist whitelist = sounds("minecraft:ui.button.click", "othermod:machine.hum");
        assertTrue(whitelist.matches("minecraft", "ui.button.click"));
        assertTrue(whitelist.matches("othermod", "machine.hum"));
        assertFalse(whitelist.matches("minecraft", "ui.button.clicks"));
        assertFalse(whitelist.matches("minecraft", "ui.button"));
        assertFalse(whitelist.matches("othermod", "ui.button.click"));
        // Dots are literal, not regex wildcards
        assertFalse(whitelist.matches("minecraft", "uiXbuttonXclick"));
    }

    @Test
    void missingNamespaceMeansMinecraft() {
        SoundWhitelist whitelist = sounds("ui.button.click");
        assertTrue(whitelist.matches("minecraft", "ui.button.click"));
        assertFalse(whitelist.matches("othermod", "ui.button.click"));
        // An empty namespace too, like ResourceLocation
        assertTrue(sounds(":ui.button.click").matches("minecraft", "ui.button.click"));
    }

    @Test
    void wildcards() {
        SoundWhitelist music = sounds("minecraft:music.*");
        assertTrue(music.matches("minecraft", "music.game"));
        assertTrue(music.matches("minecraft", "music.game.creative"));
        assertFalse(music.matches("minecraft", "music"));
        assertFalse(music.matches("minecraft", "record.cat"));
        assertFalse(music.matches("othermod", "music.game"));

        SoundWhitelist ui = sounds("*:ui.*");
        assertTrue(ui.matches("minecraft", "ui.button.click"));
        assertTrue(ui.matches("othermod", "ui.open"));
        assertFalse(ui.matches("minecraft", "entity.zombie.ambient"));

        SoundWhitelist middle = sounds("entity.*.ambient");
        assertTrue(middle.matches("minecraft", "entity.zombie.ambient"));
        assertTrue(middle.matches("minecraft", "entity.zombie_villager.ambient"));
        assertFalse(middle.matches("minecraft", "entity.zombie.hurt"));

        SoundWhitelist namespaces = sounds("other*:machine.hum");
        assertTrue(namespaces.matches("othermod", "machine.hum"));
        assertFalse(namespaces.matches("minecraft", "machine.hum"));

        // A lone * has no namespace, so it only covers minecraft
        assertTrue(sounds("*").matches("minecraft", "entity.zombie.ambient"));
        assertFalse(sounds("*").matches("othermod", "machine.hum"));
        assertTrue(sounds("*:*").matches("othermod", "machine.hum"));
    }

    @Test
    void matchingIgnoresCase() {
        assertTrue(sounds("Minecraft:UI.Button.Click").matches("minecraft", "ui.button.click"));
        assertTrue(sounds("minecraft:ui.button.click").matches("MINECRAFT", "UI.BUTTON.CLICK"));
        assertTrue(sounds("*:UI.*").matches("OtherMod", "ui.Open"));
        assertTrue(mods("OtherMod").matches("othermod", "machine.hum"));
    }

    @Test
    void modsMatchTheWholeNamespace() {
        SoundWhitelist whitelist = mods("minecraft", "othermod");
        assertTrue(whitelist.matches("minecraft", "entity.zombie.ambient"));
        assertTrue(whitelist.matches("minecraft", "music.game"));
        assertTrue(whitelist.matches("othermod", "machine.hum"));
        assertFalse(whitelist.matches("thirdmod", "machine.hum"));
        assertFalse(whitelist.matches("other", "machine.hum"));
        assertEquals(Set.of("minecraft", "othermod"), whitelist.mods());
    }

    @Test
    void blankEntriesAreSkippedSilently() {
        List<String> problems = new ArrayList<>();
        SoundWhitelist whitelist = SoundWhitelist.compile(
                new String[] {"", "   ", null, "  minecraft:ui.button.click\t"},
                new String[] {"", "\t", null},
                problems);
        assertEquals(List.of(), problems);
        assertTrue(whitelist.matches("minecraft", "ui.button.click"));
        assertEquals(Set.of(), whitelist.mods());
    }

    @Test
    void invalidEntriesAreReportedAndSkipped() {
        List<String> problems = new ArrayList<>();
        SoundWhitelist whitelist = SoundWhitelist.compile(
                new String[] {
                        "minecraft:",
                        "a:b:c",
                        "minecraft:ui button.click",
                        "\"minecraft:ui.button.click\"",
                        "minecraft:ui.button.click, minecraft:music.game",
                        "minecraft:record.cat",
                },
                new String[] {"some mod", "mod:id", "*", "othermod"},
                problems);

        assertEquals(8, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("'minecraft:'"), problems.get(0));
        assertTrue(problems.get(5).contains("'some mod'"), problems.get(5));

        assertTrue(whitelist.matches("minecraft", "record.cat"));
        assertTrue(whitelist.matches("othermod", "machine.hum"));
        assertFalse(whitelist.matches("minecraft", "ui.button.click"));
        assertFalse(whitelist.matches("minecraft", "music.game"));
        assertEquals(Set.of("othermod"), whitelist.mods());
    }

    @Test
    void emptyWhitelistMatchesNothing() {
        assertFalse(SoundWhitelist.EMPTY.matches("minecraft", "ui.button.click"));
        List<String> problems = new ArrayList<>();
        SoundWhitelist whitelist = SoundWhitelist.compile(null, new String[0], problems);
        assertFalse(whitelist.matches("minecraft", "ui.button.click"));
        assertEquals(List.of(), problems);
    }

    private static SoundWhitelist sounds(String... sounds) {
        return compile(sounds, new String[0]);
    }

    private static SoundWhitelist mods(String... mods) {
        return compile(new String[0], mods);
    }

    private static SoundWhitelist compile(String[] sounds, String[] mods) {
        List<String> problems = new ArrayList<>();
        SoundWhitelist whitelist = SoundWhitelist.compile(sounds, mods, problems);
        assertEquals(List.of(), problems);
        return whitelist;
    }
}
