package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.emulator.server.support.SingletonTestSupport;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomLeave;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomLeaveLifecycleTest {
    private GameManager gameManager;
    private Object previousGameManager;

    @BeforeEach
    void installGameManager() {
        gameManager = mock(GameManager.class);
        previousGameManager = SingletonTestSupport.replace(GameManager.class, "instance", gameManager);
    }

    @AfterEach
    void restoreGameManager() {
        SingletonTestSupport.replace(GameManager.class, "instance", previousGameManager);
    }

    @Test
    void cleanupFailureReleasesGuardSoLeaveCanBeRetried() {
        FTClient client = new FTClient();
        FTConnection connection = mock(FTConnection.class);
        client.setConnection(connection);
        when(connection.getClient()).thenReturn(client);

        doThrow(new IllegalStateException("simulated cleanup failure"))
                .doNothing()
                .when(gameManager).handleRoomPlayerChanges(connection, true);

        RoomLeaveRequestPacketHandler handler = new RoomLeaveRequestPacketHandler();
        CMSGRoomLeave request = CMSGRoomLeave.builder().build();

        assertThrows(IllegalStateException.class, () -> handler.handle(connection, request));
        assertFalse(client.getIsJoiningOrLeavingRoom().get(), "cleanup failure must release the transition guard");

        assertDoesNotThrow(() -> handler.handle(connection, request));
        verify(gameManager, times(2)).handleRoomPlayerChanges(connection, true);
    }
}
