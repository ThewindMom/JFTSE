package com.jftse.emulator.server.core.matchplay.game;

import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayLetCrystalDisappear;
import com.jftse.emulator.server.core.packets.matchplay.S2CMatchplayPlaceSkillCrystal;
import com.jftse.emulator.server.core.matchplay.MatchplayHandleable;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.protocol.PacketOperations;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchplayGuardianGameMarkerCrystalTest {
    @Test
    void markerRingUsesRequestedCenterRadiusAndCount() {
        MatchplayGuardianGame game = new TestMatchplayGuardianGame();

        List<MatchplayGuardianGame.MarkerCrystal> markers = game.addMarkerRing(10.0f, -50.0f, 6.0f, 6);

        assertEquals(6, markers.size());
        assertMarker(markers.get(0), 0, 16.0f, -50.0f);
        assertMarker(markers.get(1), 1, 13.0f, -44.80385f);
        assertMarker(markers.get(2), 2, 7.0f, -44.80385f);
        assertMarker(markers.get(3), 3, 4.0f, -50.0f);
        assertMarker(markers.get(4), 4, 7.0f, -55.19615f);
        assertMarker(markers.get(5), 5, 13.0f, -55.19615f);

        S2CMatchplayPlaceSkillCrystal packet = MatchplayGuardianGame.createMarkerPlacementPacket(markers.get(0));
        ByteBuffer payload = ByteBuffer.wrap(packet.getData()).order(ByteOrder.nativeOrder());
        assertEquals(PacketOperations.S2CMatchplayPlaceSkillCrystal.getValue(), packet.getPacketId());
        assertEquals(11, packet.getDataLength());
        assertEquals(0, payload.getShort());
        assertEquals(0, payload.get());
        assertEquals(16.0f, payload.getFloat());
        assertEquals(-50.0f, payload.getFloat());
    }

    @Test
    void clearDrainsOnlyMarkerCrystalsAndBuildsDisappearPackets() {
        MatchplayGuardianGame game = new TestMatchplayGuardianGame();
        SkillCrystal ordinaryCrystal = new SkillCrystal(40);
        game.getSkillCrystals().add(ordinaryCrystal);
        game.addMarkerCrystal(3.0f, -30.0f);
        game.addMarkerCrystal(4.0f, -31.0f);

        List<MatchplayGuardianGame.MarkerCrystal> cleared = game.drainMarkerCrystals();

        assertEquals(List.of(0, 1), cleared.stream().map(MatchplayGuardianGame.MarkerCrystal::id).toList());
        assertTrue(game.getMarkerCrystals().isEmpty());
        assertEquals(List.of(ordinaryCrystal), List.copyOf(game.getSkillCrystals()));

        S2CMatchplayLetCrystalDisappear packet = MatchplayGuardianGame.createMarkerDisappearPacket(cleared.get(1));
        ByteBuffer payload = ByteBuffer.wrap(packet.getData()).order(ByteOrder.nativeOrder());
        assertEquals(PacketOperations.S2CMatchplayLetCrystalDisappear.getValue(), packet.getPacketId());
        assertEquals(2, packet.getDataLength());
        assertEquals(1, payload.getShort());
    }

    @Test
    void markerIdsSkipIdsStillUsedByOrdinaryCrystals() {
        MatchplayGuardianGame game = new TestMatchplayGuardianGame();
        game.getSkillCrystals().add(new SkillCrystal(0));

        MatchplayGuardianGame.MarkerCrystal marker = game.addMarkerCrystal(0.0f, -10.0f);

        assertEquals(1, marker.id());
        assertFalse(game.getSkillCrystals().isEmpty());
    }

    @Test
    void randomCrystalReservationAddsItBeforeReturning() {
        MatchplayGuardianGame game = new TestMatchplayGuardianGame();
        game.addMarkerCrystal(0.0f, -10.0f);

        SkillCrystal ordinaryCrystal = game.addRandomSkillCrystal();

        assertEquals(1, ordinaryCrystal.getId());
        assertEquals(List.of(ordinaryCrystal), List.copyOf(game.getSkillCrystals()));
    }

    private void assertMarker(MatchplayGuardianGame.MarkerCrystal marker, int id, float x, float y) {
        assertEquals(id, marker.id());
        assertEquals(x, marker.x(), 0.0001f);
        assertEquals(y, marker.y(), 0.0001f);
    }

    private static final class TestMatchplayGuardianGame extends MatchplayGuardianGame {
        @Override
        protected MatchplayHandleable createHandler() {
            return null;
        }
    }
}
