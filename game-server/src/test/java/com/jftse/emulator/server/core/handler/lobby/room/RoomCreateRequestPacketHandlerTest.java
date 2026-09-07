package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.RoomCreateResult;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.RoomManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomCreate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomCreateRequestPacketHandlerTest {
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
    void failedRoomCreationReleasesJoiningGuard() {
        FTPlayer player = mock(FTPlayer.class);
        FTClient client = mock(FTClient.class);
        AtomicBoolean joiningOrLeaving = new AtomicBoolean();
        when(client.hasPlayer()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(client.getIsJoiningOrLeavingRoom()).thenReturn(joiningOrLeaving);
        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        when(client.getConnection()).thenReturn(connection);
        when(roomManager.createRoom(any(CMSGRoomCreate.class), any(FTClient.class)))
                .thenReturn(RoomCreateResult.of((char) -10, null));

        CMSGRoomCreate packet = mock(CMSGRoomCreate.class);
        when(packet.getRoomName()).thenReturn("T#1");

        new RoomCreateRequestPacketHandler().handle(connection, packet);

        verify(connection).sendTCP(org.mockito.ArgumentMatchers.any());
        assertFalse(joiningOrLeaving.get());
    }
}
