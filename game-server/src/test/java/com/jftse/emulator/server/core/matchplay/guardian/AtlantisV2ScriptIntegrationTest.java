package com.jftse.emulator.server.core.matchplay.guardian;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AtlantisV2ScriptIntegrationTest {

    private static final Path PHASE_ROOT = Path.of("src/main/resources/scripts/guardian-phase");
    private static final Path LIVE = PHASE_ROOT.resolve("10");
    private static final List<String> FORBIDDEN_SCRIPT_NAMES = List.of(
            "1_echoes_of_the_deep.js",
            "2_maelstrom_unleashed.js",
            "3_leviathans_will.js",
            "4_abyssal_reckoning.js");

    @Test
    void liveMap10FolderContainsOnlyV2ScriptsInFilenameOrder() throws IOException {
        List<String> names = jsNames(LIVE);
        assertEquals(List.of(
                "1_green_tide.js",
                "2_crab_window.js",
                "3_storm_charge.js",
                "4_enrage.js"), names);
        names.forEach(name -> assertTrue(AtlantisV2Rules.isV2PhaseScriptName(name)));
        names.forEach(name -> assertFalse(AtlantisV2Rules.isLegacyPhaseScriptName(name)));
    }

    @Test
    void oldAtlantisScriptsAreGone() throws IOException {
        assertFalse(Files.exists(PHASE_ROOT.resolve("10-legacy")));
        assertFalse(Files.exists(PHASE_ROOT.resolve("10-v2")));
        try (Stream<Path> stream = Files.walk(PHASE_ROOT)) {
            List<String> leftover = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(FORBIDDEN_SCRIPT_NAMES::contains)
                    .toList();
            assertEquals(List.of(), leftover);
        }
        String tree = walkText(PHASE_ROOT);
        assertFalse(tree.contains("Echoes of the Deep"));
        assertFalse(tree.contains("Maelstrom Unleashed"));
        assertFalse(tree.contains("Leviathan"));
        assertFalse(tree.contains("Abyssal Reckoning"));
    }

    @Test
    void liveScriptsBindJavaRulesAndConfirmedLtrSpawn() throws IOException {
        for (String name : jsNames(LIVE)) {
            String src = Files.readString(LIVE.resolve(name));
            assertTrue(src.contains("AtlantisV2Rules"), name + " must read numbers from Java");
            assertTrue(src.contains("DUMMY_ATTACKER"), name + " must fire dummy attacker 4");
            assertTrue(src.contains("AtlantisV2Rules.now()"), name + " must use the testable clock");
            assertFalse(src.contains("xyz=(-500"), name + " must not use the stale -500 spawn");
            assertFalse(src.contains("31 C9") || src.contains("xor ecx"), name + " must not mention the rejected Z-zero cave");
            assertFalse(src.toLowerCase().contains("rtl"), name + " must not invent RTL recipes");
        }
        String tide = Files.readString(LIVE.resolve("1_green_tide.js"));
        assertTrue(tide.contains("SEA_WAVE_PACKET_ID"));
        assertTrue(tide.contains("WAVE_X"));
        assertTrue(tide.contains("FIRST_VOLLEY_COUNT") || tide.contains("FIRST_VOLLEY"));
        assertTrue(tide.contains("setPlayerSupportSkillsDisabled(true)"));

        String crab = Files.readString(LIVE.resolve("2_crab_window.js"));
        assertTrue(crab.contains("setPlayerHealSkillsDisabled(false)"));
        assertTrue(crab.contains("setPlayerShieldSkillsDisabled(true)"));
        assertTrue(crab.contains("POST_REVIVE_HEAL_MULTIPLIER"));

        String enrage = Files.readString(LIVE.resolve("4_enrage.js"));
        assertTrue(enrage.contains("addsDead()"));
        assertTrue(enrage.contains("setPlayerSupportSkillsDisabled(false)"));
    }

    @Test
    void scriptManagerFactoryAssignsGroupPathTenToLiveFolder() {
        Path relative = Path.of("guardian-phase", "10", "1_green_tide.js");
        String type = relative.getName(0).toString().toUpperCase();
        String groupPath = relative.getNameCount() > 2
                ? relative.subpath(1, relative.getNameCount() - 1).toString().replace("\\", "/")
                : "";
        assertEquals("GUARDIAN-PHASE", type);
        assertEquals("10", groupPath);
        assertEquals("10", AtlantisV2Rules.resolvePhaseGroup(10));
        assertEquals(groupPath, AtlantisV2Rules.resolvePhaseGroup(10));
    }

    private static List<String> jsNames(Path dir) throws IOException {
        assertTrue(Files.isDirectory(dir), "missing " + dir.toAbsolutePath());
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".js"))
                    .sorted()
                    .toList();
        }
    }

    private static String walkText(Path dir) throws IOException {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                out.append(Files.readString(path)).append('\n');
            }
        }
        return out.toString();
    }
}
