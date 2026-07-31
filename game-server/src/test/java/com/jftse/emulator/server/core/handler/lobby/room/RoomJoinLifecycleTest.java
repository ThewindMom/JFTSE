package com.jftse.emulator.server.core.handler.lobby.room;

import com.jftse.emulator.server.core.client.FTPlayer;
import com.jftse.emulator.server.core.constants.RoomPositionState;
import com.jftse.emulator.server.core.life.room.Room;
import com.jftse.emulator.server.core.manager.GameManager;
import com.jftse.emulator.server.core.manager.ServiceManager;
import com.jftse.emulator.server.net.FTClient;
import com.jftse.emulator.server.net.FTConnection;
import com.jftse.emulator.server.support.SingletonTestSupport;
import com.jftse.server.core.protocol.IPacket;
import com.jftse.server.core.service.SocialService;
import com.jftse.server.core.shared.packets.lobby.room.CMSGRoomJoin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomJoinLifecycleTest {
    private Object previousGameManager;
    private Object previousServiceManager;

    @BeforeEach
    void installSingletons() {
        ServiceManager serviceManager = mock(ServiceManager.class);
        when(serviceManager.getSocialService()).thenReturn(mock(SocialService.class));

        previousServiceManager = SingletonTestSupport.replace(ServiceManager.class, "instance", serviceManager);
        previousGameManager = SingletonTestSupport.replace(GameManager.class, "instance", mock(GameManager.class));
    }

    @AfterEach
    void restoreSingletons() {
        SingletonTestSupport.replace(GameManager.class, "instance", previousGameManager);
        SingletonTestSupport.replace(ServiceManager.class, "instance", previousServiceManager);
    }

    @Test
    void failedJoinRollsBackMembershipAndReleasesTransitionGuard() {
        GameManager gameManager = GameManager.getInstance();
        Room room = new Room();
        room.setRoomId((short) 7);
        when(gameManager.getRooms()).thenReturn(new ConcurrentLinkedDeque<>());
        gameManager.getRooms().add(room);

        FTClient client = new FTClient();
        client.refreshPlayer(mock(FTPlayer.class));
        client.setInLobby(true);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        IllegalStateException sendFailure = new IllegalStateException("simulated send failure");
        when(connection.sendTCP(any(IPacket.class))).thenAnswer(ignored -> {
            assertTrue(client.getIsJoiningOrLeavingRoom().get(), "join must hold the transition guard while sending");
            assertSame(room, client.getActiveRoom(), "send failure must occur after activeRoom assignment");
            assertEquals(1, room.getRoomPlayerList().size(), "send failure must occur after membership is added");
            assertEquals(RoomPositionState.InUse, room.getPositions().get(0),
                    "send failure must occur after the position is reserved");
            throw sendFailure;
        });

        CMSGRoomJoin request = CMSGRoomJoin.builder().roomId(room.getRoomId()).build();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new RoomJoinRequestPacketHandler().handle(connection, request));

        assertSame(sendFailure, thrown, "join must propagate the injected send failure");
        assertNull(client.getActiveRoom(), "failed join must clear activeRoom");
        assertTrue(room.getRoomPlayerList().isEmpty(), "failed join must remove partial room membership");
        assertEquals(RoomPositionState.Free, room.getPositions().get(0),
                "failed join must release the reserved position");
        assertTrue(client.isInLobby(), "failed join must restore the prior lobby state");
        assertTrue(client.getIsJoiningOrLeavingRoom().compareAndSet(false, true),
                "a later join or leave must be able to acquire the guard");

        assertFailedHiddenGmJoinRestoresLockedPosition();
        assertPostCommitSendFailureKeepsServerMembership();
    }

    private void assertFailedHiddenGmJoinRestoresLockedPosition() {
        GameManager gameManager = GameManager.getInstance();
        Room room = new Room();
        room.setRoomId((short) 8);
        when(gameManager.getRooms()).thenReturn(new ConcurrentLinkedDeque<>());
        gameManager.getRooms().add(room);

        FTClient client = new FTClient();
        client.setGameMaster(true);
        client.refreshPlayer(mock(FTPlayer.class));

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        IllegalStateException sendFailure = new IllegalStateException("simulated GM send failure");
        when(connection.sendTCP(any(IPacket.class))).thenThrow(sendFailure);

        CMSGRoomJoin request = CMSGRoomJoin.builder().roomId(room.getRoomId()).build();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new RoomJoinRequestPacketHandler().handle(connection, request));

        assertSame(sendFailure, thrown);
        assertEquals(RoomPositionState.Locked, room.getPositions().get(9),
                "failed hidden GM join must restore the locked slot");
        assertNull(client.getActiveRoom());
        assertTrue(room.getRoomPlayerList().isEmpty());
        assertFalse(client.getIsJoiningOrLeavingRoom().get());
    }

    private void assertPostCommitSendFailureKeepsServerMembership() {
        GameManager gameManager = GameManager.getInstance();
        Room room = new Room();
        room.setRoomId((short) 9);
        when(gameManager.getRooms()).thenReturn(new ConcurrentLinkedDeque<>());
        gameManager.getRooms().add(room);

        FTClient client = new FTClient();
        client.refreshPlayer(mock(FTPlayer.class));
        client.setInLobby(true);

        FTConnection connection = mock(FTConnection.class);
        when(connection.getClient()).thenReturn(client);
        IllegalStateException sendFailure = new IllegalStateException("simulated post-commit send failure");
        when(connection.sendTCP(any(IPacket.class)))
                .thenReturn(null)
                .thenThrow(sendFailure);

        CMSGRoomJoin request = CMSGRoomJoin.builder().roomId(room.getRoomId()).build();
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new RoomJoinRequestPacketHandler().handle(connection, request));

        assertSame(sendFailure, thrown);
        assertFalse(client.getIsJoiningOrLeavingRoom().get());
        assertSame(room, client.getActiveRoom(),
                "post-commit failure must not roll back server membership");
        assertEquals(1, room.getRoomPlayerList().size());
        assertEquals(RoomPositionState.InUse, room.getPositions().get(0));
        assertFalse(client.isInLobby());
    }
}
