package com.jftse.emulator.server.core.matchplay.guardian;

import com.jftse.emulator.server.core.matchplay.combat.PlayerCombatSystem;
import com.jftse.emulator.server.core.matchplay.combat.PlayerDamageApplier;
import com.jftse.server.core.matchplay.battle.PlayerBattleState;
import com.jftse.server.core.shared.rabbit.messages.MatchCourtPositionMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuardianShieldPadsTest {
    private static final int SESSION = 4242;
    private static final int PLAYER_A = 101;
    private static final int PLAYER_B = 202;

    @Test
    void padsAreNotVisibleBeforeActivateAndVisibleAfter() {
        GuardianShieldPads pads = newPads(null, grants());
        pads.onMatchStart(SESSION);
        assertEquals(GuardianShieldPads.Phase.SCHEDULED, pads.phaseOf(SESSION));
        assertFalse(pads.isVisible(SESSION));
        assertEquals(10, pads.getConfig().getDelaySeconds());

        pads.activate(SESSION);
        assertTrue(pads.isVisible(SESSION));
        assertEquals(GuardianShieldPads.Phase.VISIBLE, pads.phaseOf(SESSION));
    }

    @Test
    void leftAndRightCoordsMatchSpawnRelativePlayerHalfDefaults() {
        GuardianShieldPads pads = newPads(null, grants());
        List<GuardianShieldPads.Pad> locations = pads.pads();
        assertEquals(2, locations.size());
        assertEquals(new GuardianShieldPads.Pad(-40, -40), locations.get(0));
        assertEquals(new GuardianShieldPads.Pad(40, -40), locations.get(1));
        // Spawn constants are Point(±20, -75). Pads stay on negative-Y player half, not on those points.
        assertTrue(locations.stream().noneMatch(p -> Math.abs(p.x()) == 20 && p.z() == -75));
        assertTrue(locations.stream().allMatch(p -> p.z() < 0));
    }

    @Test
    void containmentIsCircleOfRadius15() {
        GuardianShieldPads pads = newPads(null, grants());
        assertTrue(pads.contains(-40, -40));
        assertTrue(pads.contains(40, -40));
        assertTrue(pads.contains(-40 + 15, -40));
        assertTrue(pads.contains(40, -40 + 15));
        assertFalse(pads.contains(-40 + 16, -40));
        assertFalse(pads.contains(0, -40));
        assertFalse(pads.contains(20, -75));
        assertFalse(pads.contains(-20, -75));
        assertFalse(pads.contains(40, 40));
    }

    @Test
    void walkingOntoPadGrantsOnceAndReenterDoesNotRegrant() {
        List<int[]> grants = new ArrayList<>();
        GuardianShieldPads pads = newPads(null, (sessionId, playerId, playerPosition) ->
                grants.add(new int[]{sessionId, playerId, playerPosition}));
        pads.onMatchStart(SESSION);
        assertFalse(pads.onCourtPosition(SESSION, PLAYER_A, 0, 0, 0));
        pads.activate(SESSION);

        assertTrue(pads.onCourtPosition(SESSION, PLAYER_A, 0, -40, -40));
        assertEquals(1, grants.size());
        assertTrue(pads.hasGranted(SESSION, PLAYER_A));

        assertFalse(pads.onCourtPosition(SESSION, PLAYER_A, 0, -40, -40));
        assertFalse(pads.onCourtPosition(SESSION, PLAYER_A, 0, 40, -40));
        assertEquals(1, grants.size());

        assertTrue(pads.onCourtPosition(SESSION, PLAYER_B, 1, 40, -40));
        assertEquals(2, grants.size());
        assertTrue(pads.hasGranted(SESSION, PLAYER_B));
    }

    @Test
    void standingOnPadWhenTheyAppearStillGrantsFromLastPosition() {
        AtomicInteger grants = new AtomicInteger();
        GuardianShieldPads pads = newPads(null, (s, p, pos) -> grants.incrementAndGet());
        pads.onMatchStart(SESSION);
        pads.onCourtPosition(SESSION, PLAYER_A, 0, -40, -40);
        assertEquals(0, grants.get());
        pads.activate(SESSION);
        assertEquals(1, grants.get());
        assertTrue(pads.hasGranted(SESSION, PLAYER_A));
    }

    @Test
    void visiblePadIsSeaWaveSafeZoneWithoutConsumingGrant() {
        GuardianShieldPads pads = newPads(null, grants());
        pads.onMatchStart(SESSION);
        pads.onCourtPosition(SESSION, PLAYER_A, 0, -40, -40);
        assertFalse(pads.isInsideVisiblePad(SESSION, PLAYER_A, 0));

        pads.activate(SESSION);
        assertTrue(pads.isInsideVisiblePad(SESSION, PLAYER_A, 0));
        assertTrue(pads.hasGranted(SESSION, PLAYER_A));

        pads.onCourtPosition(SESSION, PLAYER_A, 0, 0, 0);
        assertFalse(pads.isInsideVisiblePad(SESSION, PLAYER_A, 0));
        assertTrue(pads.hasGranted(SESSION, PLAYER_A));

        pads.onCourtPosition(SESSION, PLAYER_A, 0, 40, -40);
        assertTrue(pads.isInsideVisiblePad(SESSION, PLAYER_A, 0));
        assertTrue(pads.hasGranted(SESSION, PLAYER_A));
    }

    @Test
    void guardianAnimationDoesNotOverwritePlayerPadFeet() {
        GuardianShieldPads pads = newPads(null, grants());
        pads.onMatchStart(SESSION);
        pads.activate(SESSION);
        assertTrue(pads.onCourtPosition(SESSION, PLAYER_A, 0, -40, -40));
        assertTrue(pads.isInsideVisiblePad(SESSION, PLAYER_A, 0));

        assertFalse(pads.onCourtPosition(SESSION, PLAYER_A, 12, 22, 105));
        assertTrue(pads.isInsideVisiblePad(SESSION, PLAYER_A, 0),
                "guardian pos=12 must not replace the player's last court feet");
        assertTrue(pads.isInsideVisiblePad(SESSION, 0, 0));
    }

    @Test
    void outsidePadDoesNotGrant() {
        AtomicInteger grants = new AtomicInteger();
        GuardianShieldPads pads = newPads(null, (s, p, pos) -> grants.incrementAndGet());
        pads.onMatchStart(SESSION);
        pads.activate(SESSION);
        assertFalse(pads.onCourtPosition(SESSION, PLAYER_A, 0, 0, 0));
        assertEquals(0, grants.get());
    }

    @Test
    void trackedSessionReturnsCenterOfActivePlayerCluster() {
        GuardianShieldPads pads = newPads(null, grants());
        pads.trackSession(SESSION);
        pads.onCourtPosition(SESSION, PLAYER_A, 0, -10, -60);
        pads.onCourtPosition(SESSION, PLAYER_B, 1, 10, -60);
        pads.onCourtPosition(SESSION, 303, 2, 50, -60);

        assertArrayEquals(new GuardianShieldPads.ClusterCenter[]{
                        new GuardianShieldPads.ClusterCenter(0, -60)},
                pads.clusteredPlayerCenters(SESSION, 25, Set.of(0, 1, 2)));
        assertArrayEquals(new GuardianShieldPads.ClusterCenter[0],
                pads.clusteredPlayerCenters(SESSION, 25, Set.of(0, 2)));
        assertEquals(GuardianShieldPads.Phase.NOT_ACTIVE, pads.phaseOf(SESSION));
        assertFalse(pads.isVisible(SESSION));
    }

    @Test
    void separatePlayerClustersKeepSeparateCenters() {
        GuardianShieldPads pads = newPads(null, grants());
        pads.trackSession(SESSION);
        pads.onCourtPosition(SESSION, PLAYER_A, 0, -45, -60);
        pads.onCourtPosition(SESSION, PLAYER_B, 1, -35, -60);
        pads.onCourtPosition(SESSION, 303, 2, 35, -60);
        pads.onCourtPosition(SESSION, 404, 3, 45, -60);

        assertArrayEquals(new GuardianShieldPads.ClusterCenter[]{
                        new GuardianShieldPads.ClusterCenter(-40, -60),
                        new GuardianShieldPads.ClusterCenter(40, -60)},
                pads.clusteredPlayerCenters(SESSION, 25, Set.of(0, 1, 2, 3)));
    }

    @Test
    void shieldAbsorbsOneHitThenGoneAndUnshieldedDamageApplies() {
        PlayerBattleState shielded = new PlayerBattleState((short) 0, PLAYER_A, 1000, 10, 10, 10, 10);
        shielded.setShieldActive(true);
        short afterAbsorb = PlayerDamageApplier.updateHealthByDamage(shielded, -250);
        assertEquals(1000, afterAbsorb);
        assertEquals(1000, shielded.getCurrentHealth().get());
        assertFalse(shielded.isShieldActive());
        assertFalse(shielded.isDead());

        short afterHit = PlayerDamageApplier.updateHealthByDamage(shielded, -250);
        assertEquals(750, afterHit);
        assertEquals(750, shielded.getCurrentHealth().get());

        PlayerBattleState unshielded = new PlayerBattleState((short) 1, PLAYER_B, 800, 10, 10, 10, 10);
        short applied = PlayerDamageApplier.updateHealthByDamage(unshielded, -100);
        assertEquals(700, applied);
        assertFalse(unshielded.isShieldActive());
    }

    @Test
    void playerCombatSystemHonorsShieldThroughUpdateHealthByDamage() {
        PlayerCombatSystem combat = new PlayerCombatSystem(org.mockito.Mockito.mock(com.jftse.emulator.server.core.matchplay.MatchplayGame.class));
        PlayerBattleState state = new PlayerBattleState((short) 0, PLAYER_A, 500, 10, 10, 10, 10);
        state.setShieldActive(true);
        assertEquals(500, combat.updateHealthByDamage(state, -80));
        assertFalse(state.isShieldActive());
        assertEquals(420, combat.updateHealthByDamage(state, -80));
    }

    @Test
    void healDoesNotConsumeShield() {
        PlayerBattleState state = new PlayerBattleState((short) 0, PLAYER_A, 1000, 10, 10, 10, 10);
        state.getCurrentHealth().set(400);
        state.setShieldActive(true);
        short healed = PlayerDamageApplier.updateHealthByDamage(state, 50);
        assertEquals(450, healed);
        assertTrue(state.isShieldActive());
    }

    @Test
    void cleanupClearsPadsAndWritesClearToZoneFile(@TempDir Path tmp) throws Exception {
        Path zone = tmp.resolve("stroke-quads.zone");
        GuardianShieldPads pads = newPads(zone, grants());
        pads.onMatchStart(SESSION);
        pads.activate(SESSION);
        assertEquals("pad -40 -40\npad 40 -40\n", Files.readString(zone));

        pads.onMatchEnd(SESSION);
        assertFalse(pads.isVisible(SESSION));
        assertEquals(GuardianShieldPads.Phase.NOT_ACTIVE, pads.phaseOf(SESSION));
        assertFalse(pads.hasGranted(SESSION, PLAYER_A));
        assertEquals("clear\n", Files.readString(zone));
        assertFalse(Files.exists(zone.resolveSibling("stroke-quads.zone.tmp")));
    }

    @Test
    void zoneFileWriteIsAtomicAndHasNoLeftoverTmp(@TempDir Path tmp) throws Exception {
        Path zone = tmp.resolve("stroke-quads.zone");
        GuardianShieldPads pads = newPads(zone, grants());
        pads.onMatchStart(SESSION);
        pads.activate(SESSION);
        String content = Files.readString(zone);
        assertTrue(content.contains("pad -40 -40"));
        assertTrue(content.contains("pad 40 -40"));
        assertFalse(content.contains("clear"));
        assertFalse(Files.exists(tmp.resolve("stroke-quads.zone.tmp")));
    }

    @Test
    void multiRoomVisibleSessionsSkipZoneFileWrite(@TempDir Path tmp) throws Exception {
        Path zone = tmp.resolve("stroke-quads.zone");
        GuardianShieldPads pads = newPads(zone, grants());
        pads.onMatchStart(1);
        pads.activate(1);
        assertEquals("pad -40 -40\npad 40 -40\n", Files.readString(zone));

        pads.onMatchStart(2);
        pads.activate(2);
        assertEquals(2, pads.activeVisibleSessionCount());
        // second activate skips the write; leftover content from the single-room write stays
        assertEquals("pad -40 -40\npad 40 -40\n", Files.readString(zone));

        pads.onMatchEnd(1);
        assertEquals("pad -40 -40\npad 40 -40\n", Files.readString(zone));
        pads.onMatchEnd(2);
        assertEquals("clear\n", Files.readString(zone));
    }

    @Test
    void animationAbsoluteXYMapsToCourtXZ() {
        MatchCourtPositionMessage msg = MatchCourtPositionMessage.fromAnimation(SESSION, PLAYER_A, 0, (short) -4000, (short) -4000);
        assertEquals(SESSION, msg.getGameSessionId());
        assertEquals(PLAYER_A, msg.getPlayerId());
        assertEquals(0, msg.getPlayerPosition());
        assertEquals(-40, msg.getX());
        assertEquals(-40, msg.getZ());
        assertEquals("MATCH_COURT_POSITION", msg.getMessageType());
        assertEquals("game.stats.match.court", MatchCourtPositionMessage.ROUTING_KEY);
    }

    private static GuardianShieldPads newPads(Path zoneFile, GuardianShieldPads.GrantListener listener) {
        return new GuardianShieldPads(GuardianShieldPads.Config.defaultsWithZoneFile(zoneFile), listener);
    }

    private static GuardianShieldPads.GrantListener grants() {
        return (sessionId, playerId, playerPosition) -> {
        };
    }
}
