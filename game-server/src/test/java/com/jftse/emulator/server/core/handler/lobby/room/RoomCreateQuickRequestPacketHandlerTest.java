package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.constants.GameMode;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreateQuick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomCreateQuickRequestPacketHandlerTest {
    private Object previousGameManager;
    private GameManager gameManager;

    @BeforeEach
    void setUpGameManager() {
        previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        gameManager = mock(GameManager.class);
        when(gameManager.getRoomId()).thenReturn((short) 7);
        ReflectionTestUtils.setField(GameManager.class, "instance", gameManager);
    }

    @AfterEach
    void restoreGameManager() {
        ReflectionTestUtils.setField(GameManager.class, "instance", previousGameManager);
    }

    @Test
    void quickGuardianCreationKeepsFourRoomSeats() {
        FTPlayer player = mock(FTPlayer.class);
        when(player.getName()).thenReturn("GuardianHost");
        when(player.getLevel()).thenReturn(20);

        FTConnection connection = mock(FTConnection.class);
        FTClient client = mock(FTClient.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getIsJoiningOrLeavingRoom()).thenReturn(new AtomicBoolean(false));

        CMSGRoomCreateQuick packet = CMSGRoomCreateQuick.builder()
                .roomType((byte) 0)
                .mode((byte) GameMode.GUARDIAN)
                .players((byte) 2)
                .build();

        new RoomCreateQuickRequestPacketHandler().handle(connection, packet);

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(gameManager).internalHandleRoomCreate(eq(connection), roomCaptor.capture());
        assertEquals(4, roomCaptor.getValue().getPlayers());
        assertEquals(1, roomCaptor.getValue().getAllowBattlemon());
    }
}
