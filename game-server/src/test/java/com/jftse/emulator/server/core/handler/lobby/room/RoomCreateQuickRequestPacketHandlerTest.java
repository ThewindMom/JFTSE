package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomType;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomCreateResult;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.RoomManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomCreateQuickRequestPacketHandlerTest {
    private Object previousGameManager;
    private GameManager gameManager;
    private RoomManager roomManager;

    @BeforeEach
    void setUpGameManager() {
        previousGameManager = ReflectionTestUtils.getField(GameManager.class, "instance");
        gameManager = mock(GameManager.class);
        roomManager = mock(RoomManager.class);
        when(gameManager.getRoomManager()).thenReturn(roomManager);
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

        Room room = new Room();
        room.setPlayers((byte) 4);
        room.setAllowBattlemon((byte) 0);
        room.setRoomType((byte) RoomType.MATCH);
        room.setMode((byte) GameMode.GUARDIAN);
        when(roomManager.createRoom(any(CMSGRoomCreateQuick.class), any(FTClient.class)))
                .thenReturn(RoomCreateResult.of((char) 0, room));

        CMSGRoomCreateQuick packet = CMSGRoomCreateQuick.builder()
                .roomType((byte) 0)
                .mode((byte) GameMode.GUARDIAN)
                .players((byte) 2)
                .build();

        new RoomCreateQuickRequestPacketHandler().handle(connection, packet);

        ArgumentCaptor<CMSGRoomCreateQuick> packetCaptor = ArgumentCaptor.forClass(CMSGRoomCreateQuick.class);
        verify(roomManager).createRoom(packetCaptor.capture(), any(FTClient.class));
        assertEquals(GameMode.GUARDIAN, packetCaptor.getValue().getMode());
        verify(client).setActiveRoom(room);
    }
}
