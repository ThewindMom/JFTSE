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
                "2_twin_tides.js",
                "3_rising_tide.js",
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
    void liveScriptsBindJavaRulesAndKeepSeaWavesOnTheEnemyCourt() throws IOException {
        for (String name : jsNames(LIVE)) {
            String src = Files.readString(LIVE.resolve(name));
            assertTrue(src.contains("AtlantisV2Rules"), name + " must read numbers from Java");
            assertFalse(src.contains("DUMMY_ATTACKER"), name + " must use a real living guardian attacker");
            assertTrue(src.contains("AtlantisV2Rules.now()"), name + " must use the testable clock");
            assertFalse(src.contains("xyz=(-500"), name + " must not use the stale -500 spawn");
            assertFalse(src.contains("31 C9") || src.contains("xor ecx"), name + " must not mention the rejected Z-zero cave");
            assertFalse(src.contains("game.fireSeaWave"), name + " must not use a Java SeaWave helper");
        }
        String tide = Files.readString(LIVE.resolve("1_green_tide.js"));
        assertTrue(tide.contains("castNormalSeaWave(connection, x, depth)"));
        assertTrue(tide.contains("4, 4, SEA_WAVE_PACKET_ID"));
        assertTrue(tide.contains("SEA_WAVE_PACKET_ID"));
        assertTrue(tide.contains("ADD_PHASE_TRIGGER_HEALTH"));
        assertTrue(tide.contains("BOSS_ATTACK_INTERVAL_MS"));
        assertTrue(tide.contains("ADD_ATTACK_MIN_MS"));
        assertTrue(tide.contains("ADD_ATTACK_MAX_MS"));
        assertTrue(tide.contains("setPlayerSupportSkillsDisabled(true)"));
        assertTrue(tide.contains("FIRST_VOLLEY_COUNT"));
        assertTrue(tide.contains("SECOND_VOLLEY_COUNT"));
        assertTrue(tide.contains("BOSS_FINAL_PHASE_HEALTH"));
        assertTrue(tide.contains("BOSS_NEXT_PHASE_HEALTH"));
        assertTrue(tide.contains("SEA_WAVE_DEPTHS"));
        assertTrue(tide.contains("randomWaveDepth()"));
        assertTrue(tide.contains("PHASE_ONE_WAVE_X_MIN"));
        assertTrue(tide.contains("PHASE_ONE_WAVE_X_MAX"));
        assertFalse(tide.contains("WATER_PILLAR"), "Phase 1 must not cast Water Pillar");
        assertFalse(tide.contains("STORM_SKILL"), "Phase 1 must not cast Storm");
        assertFalse(tide.contains("FIREWORK_SKILL"), "support selection must be chat-only");
        assertFalse(tide.contains("SMALL_HEAL_SKILL"), "support selection must have no pulse marker");
        assertFalse(tide.contains("runSupportMarker"), "support selection must have no visual loop");

        String twins = Files.readString(LIVE.resolve("2_twin_tides.js"));
        assertTrue(twins.contains("TWIN_TIDE_WAVE_DELTA"));
        assertTrue(twins.contains("TWIN_TIDE_PILLAR_DELTA"));
        assertTrue(twins.contains("TWIN_TIDE_BLIZZARD_DELTA"));
        assertTrue(twins.contains("TWIN_TIDE_KILL_WINDOW_MS"));
        assertTrue(twins.contains("TWIN_TIDE_REVIVE_HEALTH"));
        assertTrue(twins.contains("TWIN_TIDE_START_HEALTH"));
        assertTrue(twins.contains("TWIN_TIDE_HOMING_BALL_MS"));
        assertTrue(twins.contains("SEA_WAVE_DEPTHS"));
        assertTrue(twins.contains("randomWaveDepth()"));
        assertTrue(twins.contains("boss.getPosition(), guardian.getPosition(), rebirth.getId() - 1"));
        assertTrue(twins.contains("WATER_PILLAR_SKILL_ID"));
        assertTrue(twins.contains("POST_REVIVE_HEAL_MULTIPLIER"));
        assertTrue(twins.contains("runTidalConvergence"));
        assertTrue(twins.contains("clusteredAlivePlayerCenters"));
        assertTrue(twins.contains("center.getX(), 0, center.getZ()"));
        assertFalse(twins.contains("CRAB"));

        String rising = Files.readString(LIVE.resolve("3_rising_tide.js"));
        assertTrue(rising.contains("lowerTide()"));
        assertTrue(rising.contains("runTidalConvergence"));
        assertTrue(rising.contains("onDealDamageOnBallLossToPlayer"));
        assertTrue(rising.contains("RISING_TIDE_HOMING_BALL_MS"));
        assertTrue(rising.contains("RISING_TIDE_WATER_PILLAR_MS"));
        assertTrue(rising.contains("RISING_TIDE_BLIZZARD_MS"));
        assertTrue(rising.contains("RISING_TIDE_STORM_MS"));
        assertTrue(rising.contains("clusteredAlivePlayerCenters"));
        assertTrue(rising.contains("center.getX(), 0, center.getZ()"));
        assertFalse(rising.contains("SEA_WAVE_PACKET_ID"));
        assertFalse(rising.contains("INFERNO"), "Atlantis must not cast the magma-like Inferno attack");

        String enrage = Files.readString(LIVE.resolve("4_enrage.js"));
        assertTrue(enrage.contains("BLOOD_TIDE_BOSS_HEAL"));
        assertTrue(enrage.contains("BLOOD_TIDE_ADD_HEAL"));
        assertTrue(enrage.contains("BLOOD_TIDE_CONSUME_HEAL"));
        assertTrue(enrage.contains("clusteredAlivePlayerCenters"));
        assertTrue(enrage.contains("center.getX(), 0, center.getZ()"));
        assertTrue(enrage.contains("FINAL_POINT_WAVE_COUNT"));
        assertTrue(enrage.contains("SEA_WAVE_PACKET_ID"));
        assertTrue(enrage.contains("SEA_WAVE_DEPTHS"));
        assertTrue(enrage.contains("randomWaveDepth()"));
        assertTrue(enrage.contains("boss.getPosition(), guardian.getPosition(), rebirth.getId() - 1"));
        assertTrue(enrage.contains("setPlayerShieldSkillsDisabled(false)"));
        assertTrue(enrage.contains("runTidalConvergence"));
        assertFalse(enrage.contains("INFERNO"));
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
