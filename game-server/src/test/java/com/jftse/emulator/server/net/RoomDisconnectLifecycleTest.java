package com.jftse.emulator.server.net;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.life.room.RoomPlayer;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.support.SingletonTestSupport;
import com.jftse.entities.database.model.player.Player;
import com.jftse.server.core.service.BlockedIPService;
import com.jftse.server.core.service.PlayerService;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomDisconnectLifecycleTest {
    private Object previousGameManager;
    private Object previousServiceManager;

    @BeforeEach
    void installSingletons() {
        previousGameManager = SingletonTestSupport.replace(GameManager.class, "instance", mock(GameManager.class));
        previousServiceManager = SingletonTestSupport.replace(ServiceManager.class, "instance", mock(ServiceManager.class));
    }

    @AfterEach
    void restoreSingletons() {
        SingletonTestSupport.replace(ServiceManager.class, "instance", previousServiceManager);
        SingletonTestSupport.replace(GameManager.class, "instance", previousGameManager);
    }

    @Test
    void persistenceFailureStillRemovesRoomMembership() {
        ServiceManager serviceManager = ServiceManager.getInstance();
        PlayerService playerService = mock(PlayerService.class);
        when(serviceManager.getPlayerService()).thenReturn(playerService);
        when(serviceManager.getBlockedIPService()).thenReturn(mock(BlockedIPService.class));

        Player persistedPlayer = mock(Player.class);
        IllegalStateException persistenceFailure = new IllegalStateException("simulated persistence failure");
        when(playerService.save(persistedPlayer)).thenThrow(persistenceFailure);

        FTPlayer player = mock(FTPlayer.class);
        when(player.getId()).thenReturn(42L);
        when(player.getPlayer()).thenReturn(persistedPlayer);

        FTClient client = new FTClient();
        client.refreshPlayer(player);

        Room room = new Room();
        room.getRoomPlayerList().add(new RoomPlayer(player));
        client.setActiveRoom(room);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);

        GameManager gameManager = GameManager.getInstance();
        doAnswer(invocation -> {
            room.getRoomPlayerList().removeIf(member -> member.getPlayerId() == player.getId());
            client.setActiveRoom(null);
            return null;
        }).when(gameManager).handleRoomPlayerChanges(connection, true);

        TCPChannelHandler handler = new TCPChannelHandler(AttributeKey.valueOf("room-disconnect-lifecycle-test"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> handler.disconnected(connection));

        assertSame(persistenceFailure, thrown, "disconnect must propagate the persistence failure");
        verify(gameManager).handleRoomPlayerChanges(connection, true);
        assertNull(client.getActiveRoom(), "disconnect cleanup must clear activeRoom after persistence failure");
        assertTrue(room.getRoomPlayerList().isEmpty(),
                "disconnect cleanup must remove membership after persistence failure");
    }
}
