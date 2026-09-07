package com.jftse.emulator.server.core.packets.matchplay;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.GameSession;
import com.jftse.emulator.server.core.life.room.GameplayActor;
import com.jftse.emulator.server.net.FTClient;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S2CGameNetworkSettingsPacketTest {
    @Test
    void ordinaryRoomsKeepOneEndpointPerPlayerAndZeroPadding() {
        GameSession gameSession = mock(GameSession.class);

        S2CGameNetworkSettingsPacket packet = new S2CGameNetworkSettingsPacket(
                "", 9000, 12345, gameSession, List.of(client(11L), client(22L)));

        assertArrayEquals(payload(9000, 12345, 11, 22, 0, 0), packetPayload(packet));
    }

    @Test
    void battlemonMapsPetActorsToTheirTwoOwnerEndpoints() {
        GameSession gameSession = gameSessionWithPetOwners(true, 11L, 22L);

        S2CGameNetworkSettingsPacket packet = new S2CGameNetworkSettingsPacket(
                "", 9000, 12345, gameSession, List.of(client(11L), client(22L)));

        assertArrayEquals(payload(9000, 12345, 11, 22, 11, 22), packetPayload(packet));
    }

    @Test
    void guardianKeepsHumanEndpointsWithBothOptionalPetActors() {
        GameSession gameSession = gameSessionWithPetOwners(false, 11L, 22L);

        S2CGameNetworkSettingsPacket packet = new S2CGameNetworkSettingsPacket(
                "", 9000, 12345, gameSession, List.of(client(11L), client(22L)));

        assertArrayEquals(payload(9000, 12345, 11, 22, 0, 0), packetPayload(packet));
    }

    @Test
    void guardianKeepsHumanEndpointsWithOneOptionalPetActor() {
        GameSession gameSession = gameSessionWithPetOwners(false, 22L);

        S2CGameNetworkSettingsPacket packet = new S2CGameNetworkSettingsPacket(
                "", 9000, 12345, gameSession, List.of(client(11L), client(22L)));

        assertArrayEquals(payload(9000, 12345, 11, 22, 0, 0), packetPayload(packet));
    }

    private static GameSession gameSessionWithPetOwners(boolean battlemon, long... playerIds) {
        GameSession gameSession = mock(GameSession.class);
        when(gameSession.isDedicatedBattlemonRoom()).thenReturn(battlemon);
        for (int i = 0; i < playerIds.length; i++) {
            long playerId = playerIds[i];
            GameplayActor actor = new GameplayActor(
                    (short) (i + 2), (short) i, playerId,
                    new com.jftse.emulator.server.core.client.PetView(
                            playerId, 1, "Pet", 1, 100, 0, 0, 0, 0, 0, 0),
                    0, 0, 0, 0, 0);
            when(gameSession.getOwnedPetSeat(playerId)).thenReturn(actor);
        }
        return gameSession;
    }

    private static FTClient client(long playerId) {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(playerId);
        FTClient client = mock(FTClient.class);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        return client;
    }

    private static byte[] payload(int port, int sessionId, int... playerIds) {
        ByteBuffer buffer = ByteBuffer.allocate(24).order(ByteOrder.nativeOrder());
        buffer.putShort((short) 0); // empty UTF-16 host
        buffer.putShort((short) port);
        buffer.putInt(sessionId);
        for (int playerId : playerIds) {
            buffer.putInt(playerId);
        }
        return buffer.array();
    }

    private static byte[] packetPayload(S2CGameNetworkSettingsPacket packet) {
        return java.util.Arrays.copyOf(packet.getData(), packet.getDataLength());
    }
}
