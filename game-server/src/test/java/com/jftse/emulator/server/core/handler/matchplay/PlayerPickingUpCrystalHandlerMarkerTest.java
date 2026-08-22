package com.jftse.emulator.server.core.handler.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.matchplay.MatchplayHandleable;
import com.jftse.emulator.server.core.matchplay.game.MatchplayGuardianGame;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.matchplay.battle.SkillCrystal;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.shared.packets.matchplay.CMSGPlayerPickupCrystal;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPickingUpCrystalHandlerMarkerTest {
    @Test
    void markerPickupIsIgnoredBeforeOrdinarySkillCrystalFlow() {
        MatchplayGuardianGame game = new TestMatchplayGuardianGame();
        MatchplayGuardianGame.MarkerCrystal marker = new MatchplayGuardianGame.MarkerCrystal(7, 12.0f, -42.0f);
        game.getMarkerCrystals().add(marker);
        SkillCrystal ordinaryCrystal = new SkillCrystal(marker.id());
        game.getSkillCrystals().add(ordinaryCrystal);
        Queue<SkillCrystal> pickedUpCrystals = new LinkedBlockingQueue<>(2);

        FTConnection connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        GameSession gameSession = mock(GameSession.class);
        Room room = mock(Room.class);
        RoomPlayer roomPlayer = mock(RoomPlayer.class);
        CMSGPlayerPickupCrystal packet = mock(CMSGPlayerPickupCrystal.class);
        when(connection.getClient()).thenReturn(client);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getActiveGameSession()).thenReturn(gameSession);
        when(client.getActiveRoom()).thenReturn(room);
        when(client.getPlayer()).thenReturn(mock(FTPlayer.class));
        when(client.getRoomPlayer()).thenReturn(roomPlayer);
        when(roomPlayer.getPickedUpSkillCrystals()).thenReturn(pickedUpCrystals);
        when(gameSession.getMatchplayGame()).thenReturn(game);
        when(packet.getCrystalId()).thenReturn((short) marker.id());

        PlayerPickingUpCrystalHandler handler = mock(PlayerPickingUpCrystalHandler.class, CALLS_REAL_METHODS);

        handler.handle(connection, packet);

        assertTrue(pickedUpCrystals.isEmpty());
        assertEquals(marker, game.getMarkerCrystals().getFirst());
        assertEquals(ordinaryCrystal, game.getSkillCrystals().getFirst());
        assertEquals(-1, ordinaryCrystal.getSkillIndex());
        assertEquals(-1, ordinaryCrystal.getPickedUpByPlayerId());
        verify(connection, never()).sendTCP(any(IPacket[].class));
        verify(gameSession, never()).getFireables();
    }

    private static final class TestMatchplayGuardianGame extends MatchplayGuardianGame {
        @Override
        protected MatchplayHandleable createHandler() {
            return null;
        }
    }
}
